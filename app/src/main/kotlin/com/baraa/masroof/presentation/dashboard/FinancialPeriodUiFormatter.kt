package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.SalaryCycleStartAdjustment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FinancialPeriodUiFormatter {
    private val dayMonth: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM", Locale("ar"))

    fun formatRange(period: FinancialPeriod): String {
        val start = formatDayMonth(period.startDate)
        val end = formatDayMonth(period.displayEndDateInclusive)
        return "$start - $end"
    }

    fun formatSalaryPeriodTitle(period: FinancialPeriod): String =
        "فترة الراتب: ${formatRange(period)}"

    fun formatAdjustmentHint(adjustment: SalaryCycleStartAdjustment?): String? =
        when (adjustment) {
            SalaryCycleStartAdjustment.EARLY_FOR_FRIDAY -> "بدأت 26 لأن 27 جمعة"
            SalaryCycleStartAdjustment.LATE_FOR_SATURDAY -> "بدأت 28 لأن 27 سبت"
            null -> null
        }

    fun formatDayMonth(date: LocalDate): String = dayMonth.format(date)
}
