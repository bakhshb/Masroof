package com.baraa.masroof.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.runtime.saveable.SaverScope
import com.baraa.masroof.transaction.AccountType

class OnboardingScreenTest {
    @Test
    fun onboardingStartsAtWelcome() {
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.values().first())
        assertEquals(OnboardingStep.WELCOME, UiOnboardingState().step)
    }

    @Test
    fun onboardingDefinesPatternFirstSteps() {
        val steps = OnboardingStep.values().toSet()
        assertTrue(OnboardingStep.WELCOME in steps)
        assertTrue(OnboardingStep.PERMISSION in steps)
        assertTrue(OnboardingStep.SELECT_SENDER in steps)
        assertTrue(OnboardingStep.CREATE_PATTERN in steps)
        assertTrue(OnboardingStep.PATTERN_SUMMARY in steps)
        assertTrue(OnboardingStep.SENDER_PATTERN_SUMMARY in steps)
        assertTrue(OnboardingStep.ACCOUNT in steps)
        assertTrue(OnboardingStep.IDENTIFIERS in steps)
        assertTrue(OnboardingStep.IMPORT_PREVIEW in steps)
        assertTrue(OnboardingStep.LINK_PREVIEW in steps)
        assertTrue(OnboardingStep.IMPORT in steps)
        assertTrue(OnboardingStep.COMPLETION in steps)
        assertFalse("START_DATE was removed in v2", steps.any { it.name == "START_DATE" })
        assertFalse("OPENING_BALANCE was removed in v2", steps.any { it.name == "OPENING_BALANCE" })
    }

    @Test
    fun nextStepAdvancesThroughPatternFirstFlow() {
        assertEquals(OnboardingStep.PERMISSION, nextOnboardingStep(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.SELECT_SENDER, nextOnboardingStep(OnboardingStep.PERMISSION))
        assertEquals(OnboardingStep.CREATE_PATTERN, nextOnboardingStep(OnboardingStep.SELECT_SENDER))
        assertEquals(OnboardingStep.ACCOUNT, nextOnboardingStep(OnboardingStep.SENDER_PATTERN_SUMMARY))
        assertEquals(OnboardingStep.IDENTIFIERS, nextOnboardingStep(OnboardingStep.ACCOUNT))
        assertEquals(OnboardingStep.COMPLETION, nextOnboardingStep(OnboardingStep.IMPORT))
    }

    @Test
    fun previousStepNavigatesBackThroughPatternFirstFlow() {
        assertEquals(OnboardingStep.WELCOME, previousOnboardingStep(OnboardingStep.PERMISSION))
        assertEquals(OnboardingStep.SELECT_SENDER, previousOnboardingStep(OnboardingStep.CREATE_PATTERN))
        assertEquals(OnboardingStep.ACCOUNT, previousOnboardingStep(OnboardingStep.IDENTIFIERS))
        assertEquals(OnboardingStep.LINK_PREVIEW, previousOnboardingStep(OnboardingStep.IMPORT))
        assertEquals(OnboardingStep.WELCOME, previousOnboardingStep(OnboardingStep.WELCOME))
    }

    @Test
    fun saverRestoresFieldsThatEnableResumedButtons() {
        val original = UiOnboardingState().apply {
            step = OnboardingStep.ACCOUNT
            selectedSenderProfileId = 42L
            selectedSenderKey = "bank-key"
            selectedSenderDisplay = "البنك"
            displayName = "حساب يومي"
            patternSourceProfileId = 42L
            accountType = AccountType.BANK_ACCOUNT
            openingBalance = "125.50"
            createdAccountId = 99L
        }
        val saved = with(OnboardingSaver) {
            SaverScope { true }.save(original)
        }
        val restored = OnboardingSaver.restore(requireNotNull(saved))

        requireNotNull(restored)
        assertEquals(OnboardingStep.ACCOUNT, restored.step)
        assertEquals(42L, restored.selectedSenderProfileId)
        assertEquals("bank-key", restored.selectedSenderKey)
        assertEquals("حساب يومي", restored.displayName)
        assertEquals(42L, restored.patternSourceProfileId)
        assertEquals("125.50", restored.openingBalance)
        assertEquals(99L, restored.createdAccountId)
    }

    @Test
    fun customStartDateOptionKeepsCustomDate() {
        val state = UiOnboardingState().apply {
            option = StartDateOption.CUSTOM
            trackingDate = java.time.LocalDate.of(2025, 1, 1)
        }
        assertEquals(java.time.LocalDate.of(2025, 1, 1), state.trackingDate)
    }

    @Test
    fun setupFromTrackingDateStoresStartOfDay() {
        val state = UiOnboardingState().apply { trackingDate = java.time.LocalDate.of(2025, 5, 1) }
        val setup = setupFrom(state)
        assertEquals(
            java.time.LocalDate.of(2025, 5, 1),
            java.time.Instant.ofEpochMilli(setup.trackingStartDate)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate(),
        )
    }

    @Test
    fun identifiersRequireExplicitConfirmFlagDefaultFalse() {
        assertFalse(UiOnboardingState().identifierConfirmed)
    }
}
