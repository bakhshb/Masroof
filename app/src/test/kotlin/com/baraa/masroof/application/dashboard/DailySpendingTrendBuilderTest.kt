package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailySpendingTrendBuilderTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val period = FinancialPeriod(
        startDate = LocalDate.parse("2026-07-27"),
        endDateExclusive = LocalDate.parse("2026-07-30"),
    )

    @Test
    fun zeroFillsEveryPeriodDay_andAveragesNetDailySpending() {
        val trend = build(
            transactions = listOf(
                transaction("expense", FinancialTransactionType.EXPENSE, "100", "2026-07-27T07:00:00Z"),
                transaction("bill", FinancialTransactionType.BILL_PAYMENT, "30", "2026-07-28T07:00:00Z"),
                transaction("refund", FinancialTransactionType.REFUND, "20", "2026-07-28T08:00:00Z"),
            ),
            today = LocalDate.parse("2026-07-29"),
        )

        assertEquals(
            listOf(
                LocalDate.parse("2026-07-27"),
                LocalDate.parse("2026-07-28"),
                LocalDate.parse("2026-07-29"),
            ),
            trend.points.map { it.date },
        )
        assertEquals(
            listOf("100.00", "10.00", "0.00"),
            trend.points.map { it.spending.amount.toPlainString() },
        )
        assertEquals("36.67", trend.averageDailySpending.amount.toPlainString())
    }

    @Test
    fun excludesNonSpendingMovements_andUsesSarEquivalents() {
        val foreignExpense = transaction(
            id = "foreign",
            type = FinancialTransactionType.EXPENSE,
            amount = "10",
            occurredAt = "2026-07-27T07:00:00Z",
            currency = Currency.USD,
        )
        val trend = DailySpendingTrendBuilder.build(
            period = period,
            transactions = listOf(
                foreignExpense,
                transaction("cash", FinancialTransactionType.CASH_WITHDRAWAL, "100", "2026-07-27T07:00:00Z"),
                transaction("transfer", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100", "2026-07-27T07:00:00Z"),
                transaction("payment", FinancialTransactionType.CREDIT_CARD_PAYMENT, "100", "2026-07-27T07:00:00Z"),
                transaction("loan", FinancialTransactionType.LOAN_REPAYMENT, "100", "2026-07-27T07:00:00Z"),
            ),
            parsedRecords = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf(foreignExpense.id to Money.of("37.50", Currency.SAR)),
            zoneId = zone,
            today = LocalDate.parse("2026-07-29"),
        )

        assertEquals("37.50", trend.points.first().spending.amount.toPlainString())
        assertEquals("12.50", trend.averageDailySpending.amount.toPlainString())
    }

    @Test
    fun currentPeriod_omitsFutureDaysFromChartAndAverage() {
        val trend = build(
            transactions = listOf(
                transaction("expense", FinancialTransactionType.EXPENSE, "100", "2026-07-27T07:00:00Z"),
            ),
            today = LocalDate.parse("2026-07-28"),
        )

        assertEquals(
            listOf(
                LocalDate.parse("2026-07-27"),
                LocalDate.parse("2026-07-28"),
            ),
            trend.points.map { it.date },
        )
        assertEquals("50.00", trend.averageDailySpending.amount.toPlainString())
    }

    private fun build(
        transactions: List<FinancialTransaction>,
        today: LocalDate = LocalDate.parse("2026-07-29"),
    ): DailySpendingTrend =
        DailySpendingTrendBuilder.build(
            period = period,
            transactions = transactions,
            parsedRecords = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
            today = today,
        )

    private fun transaction(
        id: String,
        type: FinancialTransactionType,
        amount: String,
        occurredAt: String,
        currency: Currency = Currency.SAR,
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, currency),
            occurredAt = Instant.parse(occurredAt),
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )
}
