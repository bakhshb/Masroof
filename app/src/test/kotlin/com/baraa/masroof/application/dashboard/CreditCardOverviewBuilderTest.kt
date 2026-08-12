package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CreditCardOverviewBuilderTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val clock = Clock.fixed(Instant.parse("2026-08-11T17:05:00Z"), zone)
    private val card7271 = CardReference(Bank.BANK_ALJAZIRA, "7271")
    private val cardId7271 = FinancialContainerIdFactory.cardId(card7271)!!

    @Test
    fun buildsSnapshotAndStatementSpending() {
        val purchaseAt = Instant.parse("2026-08-11T17:05:00Z")
        val statementAt = Instant.parse("2026-08-10T09:00:00Z")
        val beforeStatementAt = Instant.parse("2026-08-09T12:00:00Z")

        val purchaseBody = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمblغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent().replace("بمblغ", "بمبلغ")

        val statementBody = """
            بطاقة إئتمانية: إصدار كشف حساب
            بطاقة: 7271 بطاقة إئتمانية
            إجمالي المبلغ المستحق: 3921.11 SAR
            تاريخ الاستحقاق: 07/09/2026
        """.trimIndent()

        val purchaseRaw = rawSms("sms-cc-1", purchaseBody, purchaseAt)
        val statementRaw = rawSms("sms-stmt", statementBody, statementAt)
        val purchaseEvent = parsedEvent("pe-1", purchaseRaw.id, card7271, "75.00", purchaseAt)
        val statementEvent = parsedEvent(
            id = "pe-stmt",
            rawSmsId = statementRaw.id,
            cardRef = card7271,
            amount = null,
            at = statementAt,
            family = MessageFamily.NON_FINANCIAL,
        )

        val txs = listOf(
            cardExpense("tx-after", "75.00", purchaseAt),
            cardExpense("tx-before", "50.00", beforeStatementAt),
        )

        val overview = CreditCardOverviewBuilder.build(
            statementTransactions = txs,
            parsedRecords = listOf(
                ParsedEventRecord(
                    purchaseEvent,
                    ParsedEventDetails(
                        availableBalance = Money.of("14569.09", Currency.SAR),
                        outstandingBalance = Money.of("999.00", Currency.SAR),
                    ),
                ),
                ParsedEventRecord(
                    statementEvent,
                    ParsedEventDetails(outstandingBalance = Money.of("3921.11", Currency.SAR)),
                ),
            ),
            rawSmsById = mapOf(purchaseRaw.id to purchaseRaw, statementRaw.id to statementRaw),
            zoneId = zone,
            clock = clock,
        )

        assertEquals(Money.of("3921.11", Currency.SAR), overview.aggregateDueAmount)
        assertEquals(LocalDate.parse("2026-09-07"), overview.aggregateDueDate)
        assertEquals(1, overview.cards.size)
        val row = overview.cards.single()
        assertEquals("7271", row.last4)
        assertTrue(row.isPrimary)
        assertEquals(SignedMoneyAmount.of(Money.of("75.00", Currency.SAR)), row.statementSpendingNet)
        assertEquals(Money.of("14569.09", Currency.SAR), row.snapshot?.availableBalance)
        assertEquals(Money.of("3921.11", Currency.SAR), row.snapshot?.dueAmount)
        assertTrue(overview.hasContent)
    }

    @Test
    fun madaPurchase_doesNotFeedCreditCardOverview() {
        val at = Instant.parse("2026-08-01T11:05:00Z")
        val body = """
            شراء من نقاط البيع
            بطاقة مدى: 2210
            بمبلغ: 120.00 SAR
            خصمت من حساب: 3001
        """.trimIndent()
        val raw = rawSms("sms-mada", body, at)
        val card = CardReference(Bank.BANK_ALJAZIRA, "2210")
        val event = parsedEvent("pe-mada", raw.id, card, "120.00", at)
        val overview = CreditCardOverviewBuilder.build(
            statementTransactions = emptyList(),
            parsedRecords = listOf(ParsedEventRecord(event, ParsedEventDetails())),
            rawSmsById = mapOf(raw.id to raw),
            zoneId = zone,
            clock = clock,
        )
        assertEquals(0, overview.cards.size)
        assertNull(overview.aggregateDueAmount)
    }

    private fun rawSms(id: String, body: String, at: Instant) = RawSms(
        id = id,
        sender = "AlJazira",
        body = body,
        receivedAt = at,
        deviceMessageId = id,
        bodyHash = id,
    )

    private fun cardExpense(id: String, amount: String, at: Instant) = FinancialTransaction(
        id = id,
        type = FinancialTransactionType.EXPENSE,
        amount = Money.of(amount, Currency.SAR),
        occurredAt = at,
        sourceContainerId = cardId7271,
        destinationContainerId = null,
        merchant = "shop",
        counterparty = null,
        categoryId = null,
        linkedParsedEventIds = emptyList(),
    )

    private fun parsedEvent(
        id: String,
        rawSmsId: String,
        cardRef: CardReference,
        amount: String?,
        at: Instant,
        family: MessageFamily = MessageFamily.PURCHASE,
    ) = ParsedEvent(
        id = id,
        rawSmsId = rawSmsId,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = MoneyDirection.OUTGOING,
        amount = amount?.let { Money.of(it, Currency.SAR) },
        purchaseChannel = null,
        sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
        destinationAccountRef = null,
        cardRef = cardRef,
        merchant = "shop",
        counterparty = null,
        occurredAt = at,
        bankNetworkType = null,
        confidence = Confidence(1.0),
        parseStatus = if (family == MessageFamily.NON_FINANCIAL) {
            ParseStatus.NON_FINANCIAL
        } else {
            ParseStatus.SUCCESS
        },
    )
}
