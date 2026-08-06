package com.baraa.masroof.ui.charts

import com.baraa.masroof.ledger.DailyFinancialMovement
import com.baraa.masroof.ledger.HistoricalCompletenessStatus
import com.baraa.masroof.ledger.HistoricalDataCompleteness
import com.baraa.masroof.ledger.HistoricalFinancialSummary
import com.baraa.masroof.ledger.MonthlyFinancialHistory
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class ChartMappersTest {

    @Test
    fun monthMovementSlicesOmitsZeros() {
        val movement = DailyFinancialMovement(
            income = BigDecimal("1000"),
            expenses = BigDecimal("400"),
            bankFees = BigDecimal.ZERO,
            refunds = BigDecimal("50"),
            investments = BigDecimal.ZERO,
        )
        val slices = ChartMappers.monthMovementSlices(movement)
        assertEquals(listOf("income", "expenses", "refunds"), slices.map { it.id })
        assertEquals(BigDecimal("1000"), slices.first { it.id == "income" }.value)
    }

    @Test
    fun dailyExpenseSeriesCoversAllDays() {
        val month = YearMonth.of(2024, 1)
        val day5 = month.atDay(5)
        val history = MonthlyFinancialHistory(
            month = month,
            daily = mapOf(
                day5 to summary(
                    date = day5,
                    expenses = BigDecimal("120"),
                ),
            ),
        )
        val series = ChartMappers.dailyExpenseSeries(history)
        assertEquals(31, series.size)
        assertEquals(BigDecimal("120"), series[4].value)
        assertEquals(BigDecimal.ZERO, series[0].value)
        assertTrue(ChartMappers.hasNonZero(series))
    }

    @Test
    fun dailyLiquiditySeriesUsesEndOfDay() {
        val month = YearMonth.of(2024, 2)
        val day1 = month.atDay(1)
        val history = MonthlyFinancialHistory(
            month = month,
            daily = mapOf(
                day1 to summary(date = day1, liquidity = BigDecimal("5000")),
            ),
        )
        val series = ChartMappers.dailyLiquiditySeries(history)
        assertEquals(29, series.size) // 2024 leap year February
        assertEquals(BigDecimal("5000"), series.first().value)
        assertTrue(ChartMappers.hasNonZero(series))
    }

    @Test
    fun hasNonZeroFalseWhenAllZero() {
        val points = listOf(
            DailyChartPoint(1, BigDecimal.ZERO),
            DailyChartPoint(2, BigDecimal.ZERO),
        )
        assertFalse(ChartMappers.hasNonZero(points))
    }

    private fun summary(
        date: LocalDate,
        expenses: BigDecimal = BigDecimal.ZERO,
        liquidity: BigDecimal = BigDecimal.ZERO,
    ): HistoricalFinancialSummary {
        return HistoricalFinancialSummary(
            selectedDate = date,
            defaultCurrency = Currency.SAR,
            startOfDayAssets = BigDecimal.ZERO,
            endOfDayAssets = BigDecimal.ZERO,
            startOfDayLiabilities = BigDecimal.ZERO,
            endOfDayLiabilities = BigDecimal.ZERO,
            startOfDayLiquidity = liquidity,
            endOfDayLiquidity = liquidity,
            startOfDayNetWorth = BigDecimal.ZERO,
            endOfDayNetWorth = BigDecimal.ZERO,
            movement = DailyFinancialMovement(expenses = expenses),
            postedJournalCount = 0,
            unpostedTransactionCount = 0,
            completeness = HistoricalDataCompleteness(setOf(HistoricalCompletenessStatus.COMPLETE)),
            accounts = emptyList(),
        )
    }
}
