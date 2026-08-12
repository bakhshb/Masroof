package com.baraa.masroof.presentation.dashboard

import android.content.Context
import com.baraa.masroof.R
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.SalaryCycleStartAdjustment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FinancialPeriodUiFormatter {
    fun formatRange(context: Context, period: FinancialPeriod): String {
        val dayMonth = dayMonthFormatter(context)
        val start = dayMonth.format(period.startDate)
        val end = dayMonth.format(period.displayEndDateInclusive)
        return "$start - $end"
    }

    fun formatSalaryPeriodTitle(context: Context, period: FinancialPeriod): String =
        context.getString(R.string.dashboard_salary_period_label, formatRange(context, period))

    fun formatAdjustmentHint(
        context: Context,
        adjustment: SalaryCycleStartAdjustment?,
    ): String? =
        when (adjustment) {
            SalaryCycleStartAdjustment.EARLY_FOR_FRIDAY ->
                context.getString(R.string.dashboard_period_adjustment_early_friday)
            SalaryCycleStartAdjustment.LATE_FOR_SATURDAY ->
                context.getString(R.string.dashboard_period_adjustment_late_saturday)
            null -> null
        }

    fun formatDayMonth(context: Context, date: LocalDate): String =
        dayMonthFormatter(context).format(date)

    private fun dayMonthFormatter(context: Context): DateTimeFormatter {
        val locale: Locale = context.resources.configuration.locales[0]
        return DateTimeFormatter.ofPattern("d MMMM", locale)
    }
}
