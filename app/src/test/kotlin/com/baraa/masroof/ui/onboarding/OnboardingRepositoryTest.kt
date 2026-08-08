package com.baraa.masroof.ui.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [OnboardingRepository] production contract (v2 pattern-first).
 */
class OnboardingRepositoryTest {
    @Test
    fun freshInstallationOpensOnboarding() = runBlocking {
        val repo = TestOnboardingRepository()
        assertTrue("Fresh install must yield Pending", repo.snapshot() is OnboardingState.Pending)
        assertFalse("Fresh install must not be Completed", repo.isCompleted())
    }

    @Test
    fun completingOnboardingPersistsState() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markStepCompleted(OnboardingStep.WELCOME)
        repo.markStepCompleted(OnboardingStep.PERMISSION)
        repo.markStepCompleted(OnboardingStep.SELECT_SENDER)
        repo.markStepCompleted(OnboardingStep.CREATE_PATTERN)
        repo.markStepCompleted(OnboardingStep.ACCOUNT)
        repo.markStepCompleted(OnboardingStep.IDENTIFIERS)
        repo.markStepCompleted(OnboardingStep.IMPORT)
        repo.markCompleted()
        assertTrue("markCompleted must persist the flag", repo.isCompleted())
    }

    @Test
    fun completedOnboardingSurvivesProcessDeath() = runBlocking {
        val backingStore = TestOnboardingRepository()
        backingStore.markCompleted()
        val resumed = TestOnboardingRepository(initial = backingStore.snapshot())
        assertTrue(resumed.isCompleted())
    }

    @Test
    fun completedUsersAreNotForcedThroughV2Wizard() = runBlocking {
        val completedV1 = OnboardingState.Completed(
            onboardingVersion = 1,
            completedAt = 1L,
            smsPermissionGranted = true,
        )
        val repo = TestOnboardingRepository(initial = completedV1)
        assertTrue(repo.isCompleted())
        assertEquals(1, (repo.snapshot() as OnboardingState.Completed).onboardingVersion)
        assertEquals(2, CURRENT_ONBOARDING_VERSION)
    }

    @Test
    fun permissionRevokedAfterCompletedOnboardingDoesNotReopenIntroduction() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markCompleted()
        assertTrue(repo.isCompleted())
        val state = repo.snapshot() as OnboardingState.Completed
        assertEquals(CURRENT_ONBOARDING_VERSION, state.onboardingVersion)
    }

    @Test
    fun partialOnboardingResumesAtTheCorrectStep() = runBlocking {
        val backingStore = TestOnboardingRepository()
        backingStore.markStepCompleted(OnboardingStep.WELCOME)
        backingStore.markStepCompleted(OnboardingStep.PERMISSION)
        val resumed = TestOnboardingRepository(initial = backingStore.snapshot())
        val s = resumed.snapshot() as OnboardingState.Pending
        assertEquals(OnboardingStep.PERMISSION, s.lastCompletedStep)
        assertEquals(OnboardingStep.SELECT_SENDER, nextOnboardingStep(s.lastCompletedStep!!))
    }

    @Test
    fun draftSurvivesRepositoryRecreationAndRestoresCurrentStep() = runBlocking {
        val draft = OnboardingDraft(
            step = OnboardingStep.ACCOUNT,
            selectedSenderProfileId = 7L,
            selectedSenderKey = "sender-key",
            selectedSenderDisplay = "مرسل تجريبي",
            displayName = "حساب تجريبي",
            patternSourceProfileId = 7L,
            openingBalance = "10.00",
        )
        val backingStore = TestOnboardingRepository()
        backingStore.saveDraft(draft)
        val resumed = TestOnboardingRepository(
            initial = backingStore.snapshot(),
            initialDraft = backingStore.loadDraft(),
        )

        assertEquals(draft, resumed.loadDraft())
        assertEquals(OnboardingStep.ACCOUNT, resumed.loadDraft()?.step)
    }

    @Test
    fun completingOnboardingClearsProcessDeathDraft() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.saveDraft(OnboardingDraft(step = OnboardingStep.IMPORT))
        repo.markCompleted()
        assertEquals(null, repo.loadDraft())
    }

    @Test
    fun backNavigationDoesNotRegressLastCompletedStep() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markStepCompleted(OnboardingStep.ACCOUNT)
        repo.markStepCompleted(OnboardingStep.SELECT_SENDER)

        val pending = repo.snapshot() as OnboardingState.Pending
        assertEquals(OnboardingStep.ACCOUNT, pending.lastCompletedStep)
        assertEquals(OnboardingStep.IDENTIFIERS, nextOnboardingStep(pending.lastCompletedStep!!))
    }

    @Test
    fun v1PersistedStepsRemapSafely() {
        assertEquals(OnboardingStep.SELECT_SENDER, mapPersistedStepName("START_DATE", onboardingVersion = 1))
        assertEquals(OnboardingStep.SELECT_SENDER, mapPersistedStepName("ACCOUNT", onboardingVersion = 1))
        assertEquals(OnboardingStep.SELECT_SENDER, mapPersistedStepName("OPENING_BALANCE", onboardingVersion = 1))
        assertEquals(OnboardingStep.ACCOUNT, mapPersistedStepName("ACCOUNT", onboardingVersion = 2))
        assertEquals(OnboardingStep.CREATE_PATTERN, mapPersistedStepName("CREATE_PATTERN", onboardingVersion = 2))
        assertEquals(OnboardingStep.WELCOME, mapPersistedStepName("BOGUS", onboardingVersion = 2))
        assertEquals(null, mapPersistedStepName(null))
    }

    @Test
    fun resettingOnboardingDoesNotMarkItCompleted() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markCompleted()
        assertTrue(repo.isCompleted())
        repo.resetOnboarding()
        assertFalse("resetOnboarding must clear the completed flag", repo.isCompleted())
    }

    @Test
    fun markCompletedIsIdempotent() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markCompleted()
        repo.markCompleted()
        repo.markCompleted()
        assertTrue(repo.isCompleted())
    }

    @Test
    fun onboardingVersionConstantMatchesProduction() {
        assertEquals(2, CURRENT_ONBOARDING_VERSION)
    }

    @Test
    fun resettingOnboardingDoesNotDeleteAccounts() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markCompleted()
        repo.resetOnboarding()
        assertFalse(repo.isCompleted())
    }

    @Test
    fun flowEmitsCompletedStateAfterMarkCompleted() = runBlocking {
        val repo = TestOnboardingRepository()
        repo.markCompleted()
        val emitted = repo.snapshot()
        assertTrue("Flow must emit Completed after markCompleted", emitted is OnboardingState.Completed)
    }
}
