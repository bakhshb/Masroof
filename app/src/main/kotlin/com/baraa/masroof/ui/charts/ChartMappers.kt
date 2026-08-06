package com.baraa.masroof.ui.charts

import androidx.compose.ui.graphics.Color
import com.baraa.masroof.ledger.DailyFinancialMovement
import com.baraa.masroof.ledger.MonthlyFinancialHistory
import com.baraa.masroof.ui.theme.ChartSeriesColors
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Pure mappers from ledger summaries to chart models.
 * Keep Compose out of this file so unit tests stay JVM-simple.
 */
object ChartMappers {

    data class SeriesPalette(
        val income: Color,
        val expenses: Color,
        val bankFees: Color,
        val refunds: Color,
        val investments: Color,
        val liquidity: Color,
    ) {
        companion object {
            val Light = SeriesPalette(
                income = ChartSeriesColors.Income,
                expenses = ChartSeriesColors.Expenses,
                bankFees = ChartSeriesColors.BankFees,
                refunds = ChartSeriesColors.Refunds,
                investments = ChartSeriesColors.Investments,
                liquidity = ChartSeriesColors.Liquidity,
            )
            val Dark = SeriesPalette(
                income = ChartSeriesColors.DarkIncome,
                expenses = ChartSeriesColors.DarkExpenses,
                bankFees = ChartSeriesColors.DarkBankFees,
                refunds = ChartSeriesColors.DarkRefunds,
                investments = ChartSeriesColors.DarkInvestments,
                liquidity = ChartSeriesColors.DarkLiquidity,
            )
        }
    }

    /**
     * Builds donut slices from a month movement summary.
     * Zero buckets are omitted. Values are absolute magnitudes for display.
     */
    fun monthMovementSlices(
        movement: DailyFinancialMovement,
        palette: SeriesPalette = SeriesPalette.Light,
    ): List<ChartSlice> {
        val candidates = listOf(
            ChartSlice("income", "الدخل", movement.income, palette.income),
            ChartSlice("expenses", "المصروفات", movement.expenses, palette.expenses),
            ChartSlice("bankFees", "الرسوم البنكية", movement.bankFees, palette.bankFees),
            ChartSlice("refunds", "الاستردادات", movement.refunds, palette.refunds),
            ChartSlice("investments", "الاستثمارات", movement.investments, palette.investments),
        )
        return candidates.filter { it.value.signum() > 0 }
    }

    /** Daily expense column series for the selected month. */
    fun dailyExpenseSeries(history: MonthlyFinancialHistory): List<DailyChartPoint> {
        return pointsForMonth(history.month) { day ->
            history.daily[day]?.movement?.expenses ?: BigDecimal.ZERO
        }
    }

    /** End-of-day liquidity series for financial history overview. */
    fun dailyLiquiditySeries(history: MonthlyFinancialHistory): List<DailyChartPoint> {
        return pointsForMonth(history.month) { day ->
            history.daily[day]?.endOfDayLiquidity ?: BigDecimal.ZERO
        }
    }

    fun hasNonZero(points: List<DailyChartPoint>): Boolean =
        points.any { it.value.signum() != 0 }

    private fun pointsForMonth(
        month: YearMonth,
        valueAt: (java.time.LocalDate) -> BigDecimal,
    ): List<DailyChartPoint> {
        val days = month.lengthOfMonth()
        return (1..days).map { day ->
            val date = month.atDay(day)
            DailyChartPoint(dayOfMonth = day, value = valueAt(date))
        }
    }
}
