package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.domain.period.FinancialPeriod
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

    fun formatDayMonth(date: LocalDate): String = dayMonth.format(date)
}
