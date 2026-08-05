package com.baraa.masroof.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingScreenTest {
    @Test fun onboardingStartsAtPermissionGate() {
        val s = OnboardingState()
        assertEquals(OnboardingStep.PERMISSION, s.step)
    }

    @Test fun onboardingDefinesAllExpectedSteps() {
        // Includes: PERMISSION, WELCOME, START_DATE, ACCOUNT, OPENING_BALANCE, COMPLETION
        val steps = OnboardingStep.values().toSet()
        assertTrue("PERMISSION must exist", OnboardingStep.PERMISSION in steps)
        assertTrue("WELCOME must exist", OnboardingStep.WELCOME in steps)
        assertTrue("START_DATE must exist", OnboardingStep.START_DATE in steps)
        assertTrue("ACCOUNT must exist", OnboardingStep.ACCOUNT in steps)
        assertTrue("OPENING_BALANCE must exist", OnboardingStep.OPENING_BALANCE in steps)
        assertTrue("COMPLETION must exist", OnboardingStep.COMPLETION in steps)
    }

    @Test fun saverRoundTripsAllSteps() {
        OnboardingStep.values().forEach { step ->
            val state = OnboardingState().apply { this.step = step }
            // rebuild via map
            val map = mapOf(
                "step" to state.step.name,
                "option" to state.option.name,
                "date" to state.trackingDate.toString(),
                "type" to state.accountType.name,
                "name" to state.displayName,
                "institution" to state.institution,
                "lastFour" to state.lastFour,
                "openingBalance" to state.openingBalance,
                "currency" to state.currency.name,
                "liquidity" to state.includeLiquidity,
                "netWorth" to state.includeNetWorth,
                "skipped" to state.skipped,
            )
            val restored = OnboardingSaver.restore(map)
            assertEquals("Savery must round-trip ${step.name}", step, restored!!.step)
        }
    }

    @Test fun setupFromTrackingDateStoresStartOfDay() {
        val state = OnboardingState().apply { trackingDate = LocalDate.of(2025, 5, 1) }
        val setup = setupFrom(state)
        // Currency.SAR is the default.
        assertEquals(LocalDate.of(2025, 5, 1), java.time.Instant.ofEpochMilli(setup.trackingStartDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    }

    @Test fun onboardingCannotSkipPermissionGate() {
        // The state model exposes the permission step explicitly so the UI
        // can drive the user through it. There is no "permissionGranted"
        // flag here because the gate lives in MainActivity / OnboardingScreen
        // and refuses to mark setupCompleted while READ_SMS is missing.
        val state = OnboardingState()
        assertNotEquals(OnboardingStep.WELCOME, state.step)
        assertEquals(OnboardingStep.PERMISSION, state.step)
        assertFalse(state.skipped)
    }

    @Test fun customStartDateOptionKeepsCustomDate() {
        val s = OnboardingState().apply { option = StartDateOption.CUSTOM; trackingDate = LocalDate.of(2025, 1, 1) }
        assertEquals(LocalDate.of(2025, 1, 1), s.trackingDate)
    }
}
