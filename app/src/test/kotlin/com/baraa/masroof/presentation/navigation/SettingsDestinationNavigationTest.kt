package com.baraa.masroof.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDestinationNavigationTest {
    private val alJazira = SettingsDestination.BankHub("BANK_ALJAZIRA")
    private val alJaziraCards = SettingsDestination.BankCards("BANK_ALJAZIRA")

    @Test
    fun encodeDecode_designCatalog_roundTrips() {
        val destination = SettingsDestination.DesignCatalog
        assertEquals(destination, decodeSettingsDestination(destination.encode()))
    }

    @Test
    fun pop_fromDeepLink_leavesSettings() {
        val fromCardsSummary = replaceSettingsStack(alJaziraCards)
        val fromDashboardAbout = replaceSettingsStack(SettingsDestination.About)
        val fromNotificationAbout = replaceSettingsStack(SettingsDestination.About)

        assertNull(popSettingsStack(fromCardsSummary))
        assertNull(popSettingsStack(fromDashboardAbout))
        assertNull(popSettingsStack(fromNotificationAbout))
        assertEquals(alJaziraCards, decodeSettingsDestination(fromCardsSummary.single()))
        assertEquals(SettingsDestination.About, decodeSettingsDestination(fromDashboardAbout.single()))
    }

    @Test
    fun pop_fromHubChild_returnsToHub() {
        val hub = replaceSettingsStack(SettingsDestination.Hub)
        val about = pushSettingsDestination(hub, SettingsDestination.About)

        assertEquals(hub, popSettingsStack(about))
    }

    @Test
    fun pop_walksVisitHistory_notSyntheticParents() {
        val hub = replaceSettingsStack(SettingsDestination.Hub)
        val banks = pushSettingsDestination(hub, SettingsDestination.Banks)
        val bank = pushSettingsDestination(banks, alJazira)
        val cards = pushSettingsDestination(bank, alJaziraCards)

        assertEquals(bank, popSettingsStack(cards))
        assertEquals(banks, popSettingsStack(bank))
        assertEquals(hub, popSettingsStack(banks))
        assertNull(popSettingsStack(hub))
    }

    @Test
    fun singleBankShortcutFromHub_backSkipsBanksList() {
        val hub = replaceSettingsStack(SettingsDestination.Hub)
        val bankHub = pushSettingsDestination(hub, alJazira)

        assertEquals(hub, popSettingsStack(bankHub))
    }

    @Test
    fun manageCardsThenOpenBank_backReturnsToBanksListThenOrigin() {
        val fromCardsSummary = replaceSettingsStack(SettingsDestination.Banks)
        val bank = pushSettingsDestination(fromCardsSummary, alJazira)
        val cards = pushSettingsDestination(bank, alJaziraCards)

        assertEquals(bank, popSettingsStack(cards))
        assertEquals(fromCardsSummary, popSettingsStack(bank))
        assertNull(popSettingsStack(fromCardsSummary))
    }

    @Test
    fun replaceTop_recoversMissingBankWithoutKeepingInvalidHub() {
        val hub = replaceSettingsStack(SettingsDestination.Hub)
        val invalidBank = pushSettingsDestination(hub, alJazira)

        val recovered = replaceSettingsTop(invalidBank, SettingsDestination.Banks)

        assertEquals(listOf(SettingsDestination.Hub.encode(), SettingsDestination.Banks.encode()), recovered)
        assertEquals(hub, popSettingsStack(recovered))
    }
}
