package com.baraa.masroof.ui.onboarding

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimalOnboardingPersistenceTest {
    @Test
    fun accountPersistsBeforeSenderSelectionWithoutPatterns() = runBlocking {
        val events = mutableListOf<String>()
        val state = UiOnboardingState().apply { displayName = "الحساب" }
        val repository = TestOnboardingRepository()

        persistAccountOnce(
            state = state,
            repository = repository,
            accountExists = { false },
            createAccount = {
                events += "account"
                41L
            },
            saveOptionalIdentifier = { events += "identifier" },
        )

        assertEquals(41L, state.createdAccountId)
        assertEquals(listOf("account", "identifier"), events)
        assertEquals(41L, repository.loadDraft()?.createdAccountId)
        assertFalse(repository.isCompleted())
    }

    @Test
    fun processRecreationReusesAccountAndDoesNotInsertDuplicate() = runBlocking {
        val state = UiOnboardingState().apply { createdAccountId = 41L }
        var inserts = 0

        val id = persistAccountOnce(
            state = state,
            repository = TestOnboardingRepository(),
            accountExists = { it == 41L },
            createAccount = {
                inserts += 1
                42L
            },
            saveOptionalIdentifier = {},
        )

        assertEquals(41L, id)
        assertEquals(0, inserts)
    }

    @Test
    fun senderAssociatesWithExistingAccountWithoutPatternDependency() = runBlocking {
        val state = UiOnboardingState().apply { createdAccountId = 41L }
        var association: Pair<Long, Long>? = null

        associateSelectedSender(
            state = state,
            rawSender = "JAZIRA",
            upsertSender = {
                SelectedSender(7L, "jazira", "Jazira Bank")
            },
            associateAccount = { accountId, senderId ->
                association = accountId to senderId
            },
        )

        assertEquals(41L to 7L, association)
        assertEquals(7L, state.selectedSenderProfileId)
    }

    @Test
    fun completionNeedsAccountButDoesNotNeedSenderOrApprovedPatterns() = runBlocking {
        val events = mutableListOf<String>()
        completeMinimalOnboarding(
            accountId = 41L,
            accountExists = { true },
            saveFinancialSetup = { events += "setup" },
            markCompleted = { events += "completed" },
        )
        assertEquals(listOf("setup", "completed"), events)
    }

    @Test
    fun completionNeverMarksCompletedWhenAccountIsMissing() = runBlocking {
        var completed = false
        val outcome = runCatching {
            completeMinimalOnboarding(
                accountId = 0L,
                accountExists = { false },
                saveFinancialSetup = {},
                markCompleted = { completed = true },
            )
        }
        assertTrue(outcome.isFailure)
        assertFalse(completed)
    }
}
