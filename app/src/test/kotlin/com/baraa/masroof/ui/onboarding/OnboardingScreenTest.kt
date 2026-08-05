package com.baraa.masroof.ui.onboarding

import com.baraa.masroof.data.repository.FinancialSetup
import com.baraa.masroof.data.repository.FinancialSetupRepository
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class OnboardingScreenTest {
    @Test fun onboardingStartsAtPermissionGate() {
        assertEquals(OnboardingStep.PERMISSION, OnboardingStep.values().first())
    }

    @Test fun onboardingDefinesAllExpectedSteps() {
        val steps = OnboardingStep.values().toSet()
        assertTrue("PERMISSION must exist", OnboardingStep.PERMISSION in steps)
        assertTrue("WELCOME must exist", OnboardingStep.WELCOME in steps)
        assertTrue("START_DATE must exist", OnboardingStep.START_DATE in steps)
        assertTrue("ACCOUNT must exist", OnboardingStep.ACCOUNT in steps)
        assertTrue("OPENING_BALANCE must exist", OnboardingStep.OPENING_BALANCE in steps)
        assertTrue("COMPLETION must exist", OnboardingStep.COMPLETION in steps)
    }

    @Test fun onboardingCannotSkipPermissionGate() {
        val state = UiOnboardingState()
        assertEquals(OnboardingStep.PERMISSION, state.step)
        assertFalse(state.skipped)
    }

    @Test fun customStartDateOptionKeepsCustomDate() {
        val state = UiOnboardingState().apply { option = StartDateOption.CUSTOM; trackingDate = java.time.LocalDate.of(2025, 1, 1) }
        assertEquals(java.time.LocalDate.of(2025, 1, 1), state.trackingDate)
    }

    @Test fun setupFromTrackingDateStoresStartOfDay() {
        val state = UiOnboardingState().apply { trackingDate = java.time.LocalDate.of(2025, 5, 1) }
        val setup = setupFrom(state)
        assertEquals(java.time.LocalDate.of(2025, 5, 1), java.time.Instant.ofEpochMilli(setup.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }
}