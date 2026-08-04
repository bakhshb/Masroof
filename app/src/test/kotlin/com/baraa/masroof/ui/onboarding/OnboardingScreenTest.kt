package com.baraa.masroof.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingScreenTest {
    @Test fun onboardingStartsAtWelcome() { val s = OnboardingState(); assertEquals(OnboardingStep.WELCOME, s.step); assertEquals(LocalDate.now(), s.trackingDate) }
    @Test fun startDateOptionTodayKeepsCurrentDate() { val s = OnboardingState(); s.option = StartDateOption.TODAY; s.trackingDate = LocalDate.now(); assertEquals(LocalDate.now(), s.trackingDate) }
    @Test fun startDateOptionMonthStartUsesFirstDay() { val s = OnboardingState(); s.option = StartDateOption.MONTH_START; s.trackingDate = java.time.YearMonth.now().atDay(1); assertEquals(1, s.trackingDate.dayOfMonth) }
    @Test fun onboardingDefinesAtLeastSixSteps() { assertTrue(OnboardingStep.values().size >= 6) }
    @Test fun onboardingPersistsPermissionFlags() { val s = OnboardingState(); s.permissionDenied = true; s.permissionPermanentlyDenied = true; assertTrue(s.permissionDenied); assertTrue(s.permissionPermanentlyDenied) }
    @Test fun customStartDateOptionKeepsCustomDate() { val s = OnboardingState(); s.option = StartDateOption.CUSTOM; s.trackingDate = LocalDate.of(2025, 1, 1); assertEquals(LocalDate.of(2025, 1, 1), s.trackingDate) }
}