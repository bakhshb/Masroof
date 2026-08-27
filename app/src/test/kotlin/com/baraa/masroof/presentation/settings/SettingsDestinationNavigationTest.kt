package com.baraa.masroof.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDestinationNavigationTest {
    @Test
    fun parent_skipsBanksListWhenSingleBankShortcutUsed() {
        val bankHub = SettingsDestination.BankHub("BANK_ALJAZIRA")

        assertEquals(SettingsDestination.Hub, bankHub.parent(skippedBanksList = true))
        assertEquals(SettingsDestination.Banks, bankHub.parent(skippedBanksList = false))
    }
}
