package com.baraa.masroof.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmsBindingRangeContractTest {
    @Test
    fun lastThirtyStartsTwentyNineDaysBeforeToday() {
        val today = LocalDate.of(2026, 8, 7)
        val range = SmsBindingStateHolder.resolveRange(
            SmsBindingStateHolder.RangeMode.LAST_30,
            customFrom = today,
            today = today,
        )
        assertEquals(LocalDate.of(2026, 7, 9), range.start.toLocalDate())
        assertEquals(today, range.displayEndDate)
    }

    @Test
    fun lastSalaryUsesExpectedSalaryService() {
        val today = LocalDate.of(2026, 8, 7)
        val range = SmsBindingStateHolder.resolveRange(
            SmsBindingStateHolder.RangeMode.LAST_SALARY,
            customFrom = today,
            today = today,
        )
        assertEquals(SmsBindingStateHolder.expectedSalaryDate(today), range.start.toLocalDate())
        assertEquals(today, range.displayEndDate)
    }

    @Test
    fun customFromUsesSelectedStartThroughToday() {
        val today = LocalDate.of(2026, 8, 7)
        val from = LocalDate.of(2026, 6, 1)
        val range = SmsBindingStateHolder.resolveRange(
            SmsBindingStateHolder.RangeMode.CUSTOM_FROM,
            customFrom = from,
            today = today,
        )
        assertEquals(from, range.start.toLocalDate())
        assertEquals(today, range.displayEndDate)
    }

    @Test
    fun describeRangeIncludesArabicArrow() {
        val today = LocalDate.of(2026, 8, 7)
        val label = SmsBindingStateHolder.describeRange(
            SmsBindingStateHolder.RangeMode.LAST_7,
            customFrom = today,
            today = today,
        )
        assertTrue(label.contains("→"))
    }

    @Test
    fun bindingInboxLimitIsRaisedAboveDefaultSmsCap() {
        assertTrue(SmsBindingStateHolder.BINDING_INBOX_LIMIT >= 500)
        assertTrue(com.baraa.masroof.sms.SmsRepository.DEFAULT_LIMIT >= 2000)
    }
}
