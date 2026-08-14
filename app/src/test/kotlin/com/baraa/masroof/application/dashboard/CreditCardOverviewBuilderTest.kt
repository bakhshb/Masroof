package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
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
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
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
    private val salaryPeriod = FinancialPeriodPolicy.periodContaining(LocalDate.parse("2026-08-11"))

    private val card7271 = CardReference(Bank.BANK_ALJAZIRA, "7271")
    private val card3478 = CardReference(Bank.BANK_ALJAZIRA, "3478")
    private val cardId7271 = FinancialContainerIdFactory.cardId(card7271)!!
    private val cardId3478 = FinancialContainerIdFactory.cardId(card3478)!!

    @Test
    fun aggregateDueFromStatement_purchaseDuePerCard() {
        val purchaseAt = Instant.parse("2026-08-11T17:05:00Z")
        val statementAt = Instant.parse("2026-08-10T09:00:00Z")
        val beforeStatementAt = Instant.parse("2026-08-09T12:00:00Z")
        val beforeSalaryAt = Instant.parse("2026-07-20T12:00:00Z")

        val purchase7271 = rawSms(
            id = "sms-7271",
            body = """
                شراء عبر الانترنت
                بطاقة ائتمانية: 7271
                بمبلغ: 75.00 SAR
                الرصيد المتاح: 14569.09 SAR
                إجمالي المبلغ المستحق:3921.11 SAR
            """.trimIndent(),
            at = purchaseAt,
        )
        val purchase3478 = rawSms(
            id = "sms-3478",
            body = """
                شراء عبر الانترنت
                بطاقة ائتمانية: 3478
                بمبلغ: 50.00 SAR
                الرصيد المتاح: 14644.09 SAR
                إجمالي المبلغ المستحق:500.00 SAR
            """.trimIndent(),
            at = Instant.parse("2026-08-11T16:40:00Z"),
        )
        val statement7271 = rawSms(
            id = "sms-stmt",
            body = """
                بطاقة إئتمانية: إصدار كشف حساب
                بطاقة: 7271 بطاقة إئتمانية
                إجمالي المبلغ المستحق: 0.00 SAR
                تاريخ الاستحقاق: 07/09/2026
            """.trimIndent(),
            at = statementAt,
        )

        val txs = listOf(
            cardExpense("tx-7271-after", cardId7271, "75.00", purchaseAt),
            cardExpense("tx-7271-before-stmt", cardId7271, "20.00", beforeStatementAt),
            cardExpense("tx-7271-before-salary", cardId7271, "10.00", beforeSalaryAt),
            cardExpense("tx-3478", cardId3478, "50.00", Instant.parse("2026-08-11T16:40:00Z")),
        )

        val overview = CreditCardOverviewBuilder.build(
            salaryPeriod = salaryPeriod,
            cardTransactions = txs,
            parsedRecords = listOf(
                ParsedEventRecord(
                    parsedEvent("pe-7271", purchase7271, card7271, purchaseAt),
                    ParsedEventDetails(
                        availableBalance = Money.of("14569.09", Currency.SAR),
                        outstandingBalance = Money.of("3921.11", Currency.SAR),
                    ),
                ),
                ParsedEventRecord(
                    parsedEvent("pe-3478", purchase3478, card3478, Instant.parse("2026-08-11T16:40:00Z")),
                    ParsedEventDetails(
                        availableBalance = Money.of("14644.09", Currency.SAR),
                        outstandingBalance = Money.of("500.00", Currency.SAR),
                    ),
                ),
                ParsedEventRecord(
                    parsedEvent(
                        id = "pe-stmt",
                        raw = statement7271,
                        cardRef = card7271,
                        at = statementAt,
                        family = MessageFamily.NON_FINANCIAL,
                    ),
                    ParsedEventDetails(outstandingBalance = Money.of("0.00", Currency.SAR)),
                ),
            ),
            rawSmsById = mapOf(
                purchase7271.id to purchase7271,
                purchase3478.id to purchase3478,
                statement7271.id to statement7271,
            ),
            zoneId = zone,
            clock = clock,
        )

        assertEquals(Money.of("0.00", Currency.SAR), overview.aggregateDueAmount)
        assertEquals(LocalDate.parse("2026-09-07"), overview.aggregateDueDate)
        assertEquals(statementAt, overview.aggregateDueUpdatedAt)
        assertEquals(2, overview.cards.size)

        val row7271 = overview.cards.first { it.last4 == "7271" }
        assertEquals(SignedMoneyAmount.of(Money.of("75.00", Currency.SAR)), row7271.statementSpendingNet)
        assertEquals(SignedMoneyAmount.of(Money.of("95.00", Currency.SAR)), row7271.calendarMonthSpendingNet)
        assertEquals(SignedMoneyAmount.of(Money.of("95.00", Currency.SAR)), row7271.salaryPeriodSpendingNet)
        assertEquals(Money.of("3921.11", Currency.SAR), row7271.snapshot?.dueAmount)

        val row3478 = overview.cards.first { it.last4 == "3478" }
        assertEquals(SignedMoneyAmount.of(Money.of("50.00", Currency.SAR)), row3478.statementSpendingNet)
        assertEquals(SignedMoneyAmount.of(Money.of("50.00", Currency.SAR)), row3478.calendarMonthSpendingNet)
        assertEquals(SignedMoneyAmount.of(Money.of("50.00", Currency.SAR)), row3478.salaryPeriodSpendingNet)
        assertEquals(Money.of("500.00", Currency.SAR), row3478.snapshot?.dueAmount)
        assertTrue(overview.hasContent)
    }

    @Test
    fun madaPurchase_doesNotFeedCreditCardOverview() {
        val at = Instant.parse("2026-08-01T11:05:00Z")
        val raw = rawSms(
            id = "sms-mada",
            body = """
                شراء من نقاط البيع
                بطاقة مدى: 2210
                بمبلغ: 120.00 SAR
                خصمت من حساب: 3001
            """.trimIndent(),
            at = at,
        )
        val card = CardReference(Bank.BANK_ALJAZIRA, "2210")
        val overview = CreditCardOverviewBuilder.build(
            salaryPeriod = salaryPeriod,
            cardTransactions = emptyList(),
            parsedRecords = listOf(
                ParsedEventRecord(
                    parsedEvent("pe-mada", raw, card, at),
                    ParsedEventDetails(),
                ),
            ),
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

    private fun cardExpense(id: String, cardId: String, amount: String, at: Instant) = FinancialTransaction(
        id = id,
        type = FinancialTransactionType.EXPENSE,
        amount = Money.of(amount, Currency.SAR),
        occurredAt = at,
        sourceContainerId = cardId,
        destinationContainerId = null,
        merchant = "shop",
        counterparty = null,
        categoryId = null,
        linkedParsedEventIds = emptyList(),
    )

    private fun parsedEvent(
        id: String,
        raw: RawSms,
        cardRef: CardReference,
        at: Instant,
        family: MessageFamily = MessageFamily.PURCHASE,
    ) = ParsedEvent(
        id = id,
        rawSmsId = raw.id,
        bank = Bank.BANK_ALJAZIRA,
        messageFamily = family,
        direction = MoneyDirection.OUTGOING,
        amount = Money.of("1.00", Currency.SAR),
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
