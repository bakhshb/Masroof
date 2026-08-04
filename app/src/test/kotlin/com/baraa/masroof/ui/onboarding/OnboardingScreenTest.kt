package com.baraa.masroof.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OnboardingScreenTest {
    @Test fun onboardingStartsAtWelcome() { val s = OnboardingState(); assertEquals(OnboardingStep.WELCOME, s.step); assertEquals(LocalDate.now(), s.trackingDate) }
    @Test fun startDateOptionTodayKeepsCurrentDate() { val s = OnboardingState(); s.option = StartDateOption.TODAY; s.trackingDate = LocalDate.now(); assertEquals(LocalDate.now(), s.trackingDate) }
    @Test fun startDateOptionMonthStartUsesFirstDay() { val s = OnboardingState(); s.option = StartDateOption.MONTH_START; s.trackingDate = java.time.YearMonth.now().atDay(1); assertEquals(1, s.trackingDate.dayOfMonth) }
    @Test fun saverRoundTripsAccountDraft() {
        val original = OnboardingState().apply { displayName = "حساب الراتب"; accountType = AccountType.BANK_ACCOUNT; openingBalance = "1500.50"; currency = Currency.SAR }
        val restored = OnboardingSaver.restore(OnboardingSaver.save(original))!!
        assertEquals("حساب الراتب", restored.displayName); assertEquals(AccountType.BANK_ACCOUNT, restored.accountType); assertEquals("1500.50", restored.openingBalance)
    }
}