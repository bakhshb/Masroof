package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SmsImportRangePreferredDefaultTest {

    @Test
    fun prefersOpeningBalanceWhenBeforeMonthStart() {
        val today = LocalDate.of(2026, 8, 6)
        val opening = LocalDate.of(2026, 7, 27)
        val range = SmsImportRange.preferredDefault(today, opening)
        assertEquals(SmsImportRange.QUICK_OPENING_BALANCE, range.quickId)
        assertEquals(opening, range.start.toLocalDate())
        assertEquals(today, range.displayEndDate)
    }

    @Test
    fun usesMonthStartWhenOpeningInCurrentMonth() {
        val today = LocalDate.of(2026, 8, 6)
        val opening = LocalDate.of(2026, 8, 1)
        val range = SmsImportRange.preferredDefault(today, opening)
        assertEquals(SmsImportRange.QUICK_MONTH_START, range.quickId)
    }
}
