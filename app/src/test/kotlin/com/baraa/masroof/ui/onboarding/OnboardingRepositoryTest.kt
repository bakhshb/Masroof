package com.baraa.masroof.ui.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRepositoryTest {
    @Test
    fun freshInstallationIsPendingAndUsesVersionThree() {
        val repo = TestOnboardingRepository()
        assertTrue(repo.snapshot() is OnboardingState.Pending)
        assertFalse(repo.isCompleted())
        assertEquals(3, CURRENT_ONBOARDING_VERSION)
    }

    @Test
    fun minimalFlowOrderIsStable() {
        assertEquals(OnboardingStep.ACCOUNT, nextOnboardingStep(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.SELECT_SENDER, nextOnboardingStep(OnboardingStep.ACCOUNT))
        assertEquals(OnboardingStep.COMPLETION, nextOnboardingStep(OnboardingStep.SELECT_SENDER))
        assertEquals(OnboardingStep.SELECT_SENDER, previousOnboardingStep(OnboardingStep.COMPLETION))
    }

    @Test
    fun oldRemovedStepWithoutAccountMigratesToAccount() {
        assertEquals(
            OnboardingStep.ACCOUNT,
            mapPersistedStepName("CREATE_PATTERN", onboardingVersion = 2),
        )
        assertEquals(
            OnboardingStep.ACCOUNT,
            mapPersistedStepName("IMPORT_PREVIEW", onboardingVersion = 2),
        )
    }

    @Test
    fun oldRemovedStepWithAccountMigratesToSenderSelection() {
        assertEquals(
            OnboardingStep.SELECT_SENDER,
            mapPersistedStepName(
                "CREATE_PATTERN",
                onboardingVersion = 2,
                createdAccountId = 17L,
            ),
        )
    }

    @Test
    fun oldPostAccountDraftWithSenderCanResumeAtCompletion() {
        assertEquals(
            OnboardingStep.COMPLETION,
            mapPersistedStepName(
                "IMPORT",
                onboardingVersion = 2,
                createdAccountId = 17L,
                selectedSenderProfileId = 8L,
            ),
        )
    }

    @Test
    fun removedOrUnknownCurrentStepNeverUsesOrdinalRestoration() {
        assertEquals(
            OnboardingStep.ACCOUNT,
            mapPersistedStepName("CREATE_PATTERN", onboardingVersion = 3),
        )
        assertEquals(OnboardingStep.ACCOUNT, mapPersistedStepName("BOGUS", onboardingVersion = 3))
        assertNull(mapPersistedStepName(null))
    }

    @Test
    fun processDraftKeepsCreatedAccountIdForReuse() {
        val draft = OnboardingDraft(
            step = OnboardingStep.SELECT_SENDER,
            displayName = "حساب يومي",
            createdAccountId = 99L,
        )
        val repo = TestOnboardingRepository(initialDraft = draft)
        assertEquals(99L, repo.loadDraft()?.createdAccountId)
        assertEquals(OnboardingStep.SELECT_SENDER, repo.loadDraft()?.step)
    }

    @Test
    fun completionClearsDraftAndIsIdempotent() = runBlocking {
        val repo = TestOnboardingRepository(
            initialDraft = OnboardingDraft(
                step = OnboardingStep.COMPLETION,
                createdAccountId = 1L,
            ),
        )
        repo.markCompleted()
        repo.markCompleted()
        assertTrue(repo.isCompleted())
        assertNull(repo.loadDraft())
    }

    @Test
    fun completedLegacyUsersNeverBecomePending() {
        val completed = OnboardingState.Completed(
            onboardingVersion = 1,
            completedAt = 1L,
            smsPermissionGranted = false,
        )
        val repo = TestOnboardingRepository(initial = completed)
        assertTrue(repo.isCompleted())
        assertTrue(repo.snapshot() is OnboardingState.Completed)
    }
}
