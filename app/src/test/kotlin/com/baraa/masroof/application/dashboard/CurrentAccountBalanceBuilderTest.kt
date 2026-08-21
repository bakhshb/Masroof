package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CurrentAccountBalanceBuilderTest {
    private val account3001 = registry("3001")
    private val account3003 = registry("3003")
    private val id3001 = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3001")
    private val id3003 = FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "3003")
    private val snapshotAt = Instant.parse("2026-08-03T08:00:00Z")

    @Test
    fun balanceNotice_setsRemainingForThatAccountOnly() {
        val notice = balanceNoticeSms("3001", "17230.03", snapshotAt)
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001, account3003),
            parsedRecords = listOf(balanceNoticeRecord(notice, "3001", "17230.03", snapshotAt)),
            rawSmsById = mapOf(notice.id to notice),
        )
        val remaining = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
        )

        assertEquals(SignedMoneyAmount.of(Money.of("17230.03", Currency.SAR)), remaining[id3001])
        assertNull(remaining[id3003])
    }

    @Test
    fun creditCardAvailableBalance_doesNotBecomeCurrentAccountRemaining() {
        val sms = rawSms(
            id = "sms-cc",
            body = """
                شراء عبر الانترنت
                بطاقة ائتمانية: 7271
                بمبلغ: 75.00 SAR
                الرصيد المتاح: 14569.09 SAR
                إجمالي المبلغ المستحق:3921.11 SAR
            """.trimIndent(),
            at = snapshotAt,
        )
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001),
            parsedRecords = listOf(
                ParsedEventRecord(
                    parsedEvent(
                        id = "pe-cc",
                        raw = sms,
                        at = snapshotAt,
                        family = MessageFamily.PURCHASE,
                        sourceLast4 = "3001",
                        cardLast4 = "7271",
                    ),
                    ParsedEventDetails(availableBalance = Money.of("14569.09", Currency.SAR)),
                ),
            ),
            rawSmsById = mapOf(sms.id to sms),
        )

        assertEquals(emptyMap<String, CurrentAccountBalanceSnapshot>(), snapshots)
    }

    @Test
    fun laterAccountExpense_rollsRemainingForward() {
        val notice = balanceNoticeSms("3001", "1000.00", snapshotAt)
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001),
            parsedRecords = listOf(balanceNoticeRecord(notice, "3001", "1000.00", snapshotAt)),
            rawSmsById = mapOf(notice.id to notice),
        )
        val remaining = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = listOf(
                tx(
                    id = "pos",
                    type = FinancialTransactionType.EXPENSE,
                    amount = "200.00",
                    at = Instant.parse("2026-08-03T10:00:00Z"),
                    source = id3001,
                ),
            ),
            primaryCurrency = Currency.SAR,
        )

        assertEquals(SignedMoneyAmount.of(Money.of("800.00", Currency.SAR)), remaining[id3001])
    }

    @Test
    fun selfTransferAfterSnapshot_movesRemainingBetweenAccounts() {
        val notice3001 = balanceNoticeSms("3001", "1000.00", snapshotAt)
        val notice3003 = balanceNoticeSms("3003", "0.00", snapshotAt, id = "sms-3003")
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001, account3003),
            parsedRecords = listOf(
                balanceNoticeRecord(notice3001, "3001", "1000.00", snapshotAt),
                balanceNoticeRecord(notice3003, "3003", "0.00", snapshotAt, eventId = "pe-3003"),
            ),
            rawSmsById = mapOf(notice3001.id to notice3001, notice3003.id to notice3003),
        )
        val remaining = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = listOf(
                tx(
                    id = "self",
                    type = FinancialTransactionType.SELF_TRANSFER,
                    amount = "400.00",
                    at = Instant.parse("2026-08-03T10:38:00Z"),
                    source = id3001,
                    dest = id3003,
                ),
            ),
            primaryCurrency = Currency.SAR,
        )

        assertEquals(SignedMoneyAmount.of(Money.of("600.00", Currency.SAR)), remaining[id3001])
        assertEquals(SignedMoneyAmount.of(Money.of("400.00", Currency.SAR)), remaining[id3003])
    }

    @Test
    fun expenseWithNullSource_doesNotChangeAnyAccountRemaining() {
        val notice = balanceNoticeSms("3001", "1000.00", snapshotAt)
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001, account3003),
            parsedRecords = listOf(balanceNoticeRecord(notice, "3001", "1000.00", snapshotAt)),
            rawSmsById = mapOf(notice.id to notice),
        )
        val remaining = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = listOf(
                tx(
                    id = "orphan",
                    type = FinancialTransactionType.EXPENSE,
                    amount = "200.00",
                    at = Instant.parse("2026-08-03T10:00:00Z"),
                    source = null,
                ),
            ),
            primaryCurrency = Currency.SAR,
        )

        assertEquals(SignedMoneyAmount.of(Money.of("1000.00", Currency.SAR)), remaining[id3001])
    }

    @Test
    fun transactionAtSnapshotTime_isNotAppliedAgain() {
        val notice = balanceNoticeSms("3001", "800.00", snapshotAt)
        val snapshots = CurrentAccountBalanceBuilder.latestSnapshots(
            ownedAccounts = listOf(account3001),
            parsedRecords = listOf(balanceNoticeRecord(notice, "3001", "800.00", snapshotAt)),
            rawSmsById = mapOf(notice.id to notice),
        )
        val remaining = CurrentAccountBalanceBuilder.remainingByAccount(
            snapshots = snapshots,
            transactions = listOf(
                tx(
                    id = "same-time",
                    type = FinancialTransactionType.EXPENSE,
                    amount = "200.00",
                    at = snapshotAt,
                    source = id3001,
                ),
            ),
            primaryCurrency = Currency.SAR,
        )

        assertEquals(SignedMoneyAmount.of(Money.of("800.00", Currency.SAR)), remaining[id3001])
    }

    private fun registry(last4: String) = AccountRegistryEntry(
        bank = Bank.BANK_ALJAZIRA,
        maskedNumber = last4,
        ownership = OwnershipStatus.OWNED,
        firstSeenRawSmsId = null,
        lastSeenRawSmsId = null,
    )

    private fun balanceNoticeSms(
        last4: String,
        amount: String,
        at: Instant,
        id: String = "sms-balance-$last4",
    ) = rawSms(
        id = id,
        body = """
            إشعار رصيد
            حساب: $last4
            الرصيد المتاح: SAR $amount
            في: 2026-08-03 08:00
        """.trimIndent(),
        at = at,
    )

    private fun balanceNoticeRecord(
        raw: RawSms,
        last4: String,
        amount: String,
        at: Instant,
        eventId: String = "pe-${raw.id}",
    ) = ParsedEventRecord(
        parsedEvent(
            id = eventId,
            raw = raw,
            at = at,
            family = MessageFamily.BALANCE_NOTICE,
            sourceLast4 = last4,
        ),
        ParsedEventDetails(availableBalance = Money.of(amount, Currency.SAR)),
    )

    private fun rawSms(id: String, body: String, at: Instant) = RawSms(
        id = id,
        sender = "AlJazira",
        body = body,
        receivedAt = at,
        deviceMessageId = id,
        bodyHash = id,
    )

    private fun parsedEvent(
        id: String,
        raw: RawSms,
        at: Instant,
        family: MessageFamily,
        sourceLast4: String?,
        cardLast4: String? = null,
    ) = ParsedEvent(
        id = id,
        rawSmsId = raw.id,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = MoneyDirection.INCOMING,
        amount = null,
        purchaseChannel = null,
        sourceAccountRef = sourceLast4?.let { AccountReference(Bank.BANK_ALJAZIRA, it) },
        destinationAccountRef = null,
        cardRef = cardLast4?.let { CardReference(Bank.BANK_ALJAZIRA, it) },
        merchant = null,
        counterparty = null,
        occurredAt = at,
        bankNetworkType = null,
        confidence = Confidence(1.0),
        parseStatus = if (family == MessageFamily.BALANCE_NOTICE) {
            ParseStatus.NON_FINANCIAL
        } else {
            ParseStatus.SUCCESS
        },
    )

    private fun tx(
        id: String,
        type: FinancialTransactionType,
        amount: String,
        at: Instant,
        source: String?,
        dest: String? = null,
    ) = FinancialTransaction(
        id = id,
        type = type,
        amount = Money.of(amount, Currency.SAR),
        occurredAt = at,
        sourceContainerId = source,
        destinationContainerId = dest,
        merchant = null,
        counterparty = null,
        categoryId = null,
        linkedParsedEventIds = emptyList(),
    )
}
