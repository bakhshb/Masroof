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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreditCardOverviewBuilderTest {
    private val card7271 = CardReference(Bank.BANK_ALJAZIRA, "7271")
    private val cardId7271 = FinancialContainerIdFactory.cardId(card7271)!!

    @Test
    fun buildsSnapshotAndPeriodSpending() {
        val at = Instant.parse("2026-08-11T17:05:00Z")
        val body = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent()
        val raw = RawSms(
            id = "sms-cc-1",
            sender = "AlJazira",
            body = body,
            receivedAt = at,
            deviceMessageId = "1",
            bodyHash = "hash",
        )
        val event = parsedEvent(
            id = "pe-1",
            rawSmsId = raw.id,
            cardRef = card7271,
            amount = "75.00",
            at = at,
        )
        val details = ParsedEventDetails(
            availableBalance = Money.of("14569.09", Currency.SAR),
            outstandingBalance = Money.of("3921.11", Currency.SAR),
        )
        val tx = FinancialTransaction(
            id = TransactionIdFactory.fromRawSmsIds(listOf(raw.id)),
            type = FinancialTransactionType.EXPENSE,
            amount = Money.of("75.00", Currency.SAR),
            occurredAt = at,
            sourceContainerId = cardId7271,
            destinationContainerId = null,
            merchant = "ananinja.com",
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = listOf(event.id),
        )

        val overview = CreditCardOverviewBuilder.build(
            periodTransactions = listOf(tx),
            parsedRecords = listOf(ParsedEventRecord(event, details)),
            rawSmsById = mapOf(raw.id to raw),
        )

        assertEquals(Money.of("3921.11", Currency.SAR), overview.aggregateDueAmount)
        assertEquals(1, overview.cards.size)
        val row = overview.cards.single()
        assertEquals("7271", row.last4)
        assertEquals(SignedMoneyAmount.of(Money.of("75.00", Currency.SAR)), row.periodSpendingNet)
        assertEquals(Money.of("14569.09", Currency.SAR), row.snapshot?.availableBalance)
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
        val raw = RawSms(
            id = "sms-mada",
            sender = "AlJazira",
            body = body,
            receivedAt = at,
            deviceMessageId = "2",
            bodyHash = "hash2",
        )
        val card = CardReference(Bank.BANK_ALJAZIRA, "2210")
        val event = parsedEvent(
            id = "pe-mada",
            rawSmsId = raw.id,
            cardRef = card,
            amount = "120.00",
            at = at,
        )
        val overview = CreditCardOverviewBuilder.build(
            periodTransactions = emptyList(),
            parsedRecords = listOf(ParsedEventRecord(event, ParsedEventDetails())),
            rawSmsById = mapOf(raw.id to raw),
        )
        assertEquals(0, overview.cards.size)
    }

    private fun parsedEvent(
        id: String,
        rawSmsId: String,
        cardRef: CardReference,
        amount: String,
        at: Instant,
    ) = ParsedEvent(
        id = id,
        rawSmsId = rawSmsId,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = MessageFamily.PURCHASE,
        direction = MoneyDirection.OUTGOING,
        amount = Money.of(amount, Currency.SAR),
        purchaseChannel = null,
        sourceAccountRef = AccountReference(Bank.BANK_ALJAZIRA, "3001"),
        destinationAccountRef = null,
        cardRef = cardRef,
        merchant = "shop",
        counterparty = null,
        occurredAt = at,
        bankNetworkType = null,
        confidence = Confidence(1.0),
        parseStatus = ParseStatus.SUCCESS,
    )
}
