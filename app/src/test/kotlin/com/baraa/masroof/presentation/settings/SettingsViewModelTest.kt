package com.baraa.masroof.presentation.settings

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.CardRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_groupsCardsByOwnership() = runTest {
        val registry = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.OWNED, "1", "1"),
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "5123", OwnershipStatus.UNKNOWN, "2", "2"),
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        val vm = viewModel(registry)
        vm.refresh()
        advanceUntilIdle()

        assertEquals("7271", vm.uiState.value.followedCards.single().last4)
        assertEquals("5123", vm.uiState.value.unregisteredCards.single().last4)
        assertEquals("9999", vm.uiState.value.stoppedCards.single().last4)
        assertEquals("1.0-test", vm.uiState.value.appVersion)
    }

    @Test
    fun resumeTracking_marksCardOwned() = runTest {
        val registry = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL, "3", "3"),
        )
        var refreshCalls = 0
        val vm = viewModel(registry) { refreshCalls++ }
        vm.refresh()
        advanceUntilIdle()
        vm.resumeTracking(ManagedCardUi(Bank.BANK_ALJAZIRA, "9999", OwnershipStatus.EXTERNAL))
        advanceUntilIdle()

        assertEquals(OwnershipStatus.OWNED, registry.entries.single().ownership)
        assertEquals(1, refreshCalls)
        assertTrue(vm.uiState.value.followedCards.any { it.last4 == "9999" })
    }

    @Test
    fun confirmStopTracking_marksCardExternal() = runTest {
        val registry = FakeCardRegistry(
            CardRegistryEntry(Bank.BANK_ALJAZIRA, "7271", OwnershipStatus.OWNED, "1", "1"),
        )
        val vm = viewModel(registry)
        vm.refresh()
        advanceUntilIdle()
        val card = vm.uiState.value.followedCards.single()
        vm.requestStopTracking(card)
        vm.confirmStopTracking()
        advanceUntilIdle()

        assertEquals(OwnershipStatus.EXTERNAL, registry.entries.single().ownership)
        assertNull(vm.uiState.value.stopConfirmTarget)
        assertTrue(vm.uiState.value.stoppedCards.any { it.last4 == "7271" })
    }

    private fun viewModel(
        registry: CardRegistryRepository,
        onRefreshReviewQueue: () -> Unit = {},
    ): SettingsViewModel =
        SettingsViewModel(
            cardRegistryRepository = registry,
            ownershipConfirmationService = OwnershipConfirmationService(
                accountRegistry = object : com.baraa.masroof.domain.repository.AccountRegistryRepository {
                    override suspend fun observe(
                        reference: com.baraa.masroof.domain.model.AccountReference,
                        rawSmsId: String,
                    ) = Unit

                    override suspend fun setOwnership(
                        reference: com.baraa.masroof.domain.model.AccountReference,
                        status: OwnershipStatus,
                    ) = Unit

                    override suspend fun resolve(
                        reference: com.baraa.masroof.domain.model.AccountReference,
                    ) = OwnershipStatus.UNKNOWN

                    override suspend fun get(ref: com.baraa.masroof.domain.model.AccountReference) = null
                    override suspend fun listAll() =
                        emptyList<com.baraa.masroof.domain.model.AccountRegistryEntry>()
                },
                cardRegistry = registry,
            ),
            refreshReviewQueue = { onRefreshReviewQueue() },
            reparseStoredEvents = { 0 },
            appVersion = "1.0-test",
        )

    private class FakeCardRegistry(
        vararg initial: CardRegistryEntry,
    ) : CardRegistryRepository {
        val entries = initial.toMutableList()

        override suspend fun observe(reference: CardReference, rawSmsId: String) = Unit

        override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
            val index = entries.indexOfFirst { it.bank == reference.bank && it.last4 == reference.last4 }
            if (index >= 0) {
                entries[index] = entries[index].copy(ownership = status)
            }
        }

        override suspend fun resolve(reference: CardReference): OwnershipStatus =
            entries.find { it.bank == reference.bank && it.last4 == reference.last4 }?.ownership
                ?: OwnershipStatus.UNKNOWN

        override suspend fun get(reference: CardReference) =
            entries.find { it.bank == reference.bank && it.last4 == reference.last4 }

        override suspend fun listAll(): List<CardRegistryEntry> = entries.toList()
    }
}
