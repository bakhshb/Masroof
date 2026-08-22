package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DebitCardOverviewBuilderTest {
    private val zoneId = ZoneId.of("Asia/Riyadh")
    private val salaryPeriod = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-11"))

    @Test
    fun madaPosAndCashWithdrawal_sumForLinkedDebitCard() {
        val owned = "account:bank_aljazira:3001"
        val cardId = "card:bank_aljazira:2210"
        val pos = tx(
            id = "pos-mada",
            type = FinancialTransactionType.EXPENSE,
            amount = "120.00",
            source = cardId,
            linked = listOf("evt-pos"),
        )
        val cash = tx(
            id = "cash-mada",
            type = FinancialTransactionType.CASH_WITHDRAWAL,
            amount = "200.00",
            source = owned,
            linked = listOf("evt-cash"),
        )
        val parsedRecords = listOf(
            parsedRecord(
                id = "evt-pos",
                family = MessageFamily.PURCHASE,
                sourceLast4 = "3001",
                cardLast4 = "2210",
                rawBody = "شراء من نقاط البيع\nبطاقة مدى: 2210",
            ),
            parsedRecord(
                id = "evt-cash",
                family = MessageFamily.WITHDRAWAL,
                sourceLast4 = "3001",
                cardLast4 = "2210",
                rawBody = "سحب نقدي\nبطاقة مدى: 2210",
            ),
        )
        val rawSmsById = parsedRecords.associate { record ->
            record.event.rawSmsId to RawSms(
                id = record.event.rawSmsId,
                sender = "AlJazira",
                body = record.event.counterparty.orEmpty(),
                receivedAt = Instant.parse("2026-08-05T11:05:00Z"),
                deviceMessageId = record.event.id,
                bodyHash = record.event.id,
            )
        }
        val debit = CardRegistryEntry(
            bank = Bank.BANK_ALJAZIRA,
            last4 = "2210",
            ownership = OwnershipStatus.OWNED,
            cardType = CardType.DEBIT,
            linkedAccountBankId = Bank.BANK_ALJAZIRA.id,
            linkedAccountMaskedNumber = "3001",
            firstSeenRawSmsId = "sms",
            lastSeenRawSmsId = "sms",
        )

        val (spending, _) = DebitCardOverviewBuilder.buildSpendingByCardKey(
            salaryPeriod = salaryPeriod,
            debitCards = listOf(debit),
            transactions = listOf(pos, cash),
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            ownedAccountContainerIds = setOf(owned),
            ownedAccountLast4s = setOf("3001"),
            zoneId = zoneId,
        )

        assertEquals(BigDecimal("320.00"), spending["BANK_ALJAZIRA:2210"]?.amount)
    }

    private fun tx(
        id: String,
        type: FinancialTransactionType,
        amount: String,
        source: String?,
        linked: List<String>,
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = Instant.parse("2026-08-05T11:05:00Z"),
            sourceContainerId = source,
            destinationContainerId = null,
            linkedParsedEventIds = linked,
            merchant = null,
            counterparty = null,
            categoryId = null,
        )

    private fun parsedRecord(
        id: String,
        family: MessageFamily,
        sourceLast4: String,
        cardLast4: String,
        rawBody: String,
    ): ParsedEventRecord {
        val event = ParsedEvent(
            id = id,
            rawSmsId = "sms-$id",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = family,
            direction = MoneyDirection.OUTGOING,
            amount = Money.of("1.00", Currency.SAR),
            purchaseChannel = null,
            sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, sourceLast4),
            destinationAccountRef = null,
            cardRef = CardReference(Bank.BANK_ALJAZIRA, cardLast4),
            merchant = null,
            counterparty = rawBody,
            occurredAt = Instant.parse("2026-08-05T11:05:00Z"),
            bankNetworkType = null,
            confidence = Confidence(1.0),
            parseStatus = ParseStatus.SUCCESS,
        )
        return ParsedEventRecord(event = event, details = ParsedEventDetails())
    }
}
