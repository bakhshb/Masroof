package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.domain.model.Bank
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardRegistryLabelsTest {
    @Test
    fun resolveAccountLabel_customNameWins() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "  Home  ",
            maskedNumber = "3001",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Home", label)
    }

    @Test
    fun resolveAccountLabel_blankFallsBackToLast4() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "   ",
            maskedNumber = "3001",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Account ···3001", label)
    }

    @Test
    fun resolveCardLabel_customNameWins() {
        val label = DashboardRegistryLabels.resolveCardLabel(
            displayName = "Travel",
            last4 = "7271",
            last4Template = { "Card ···$it" },
        )
        assertEquals("Travel", label)
    }

    @Test
    fun accountLabel_lookupByBankAndMaskedNumber() {
        val accounts = listOf(
            OwnedAccountUi(
                bank = Bank.BANK_ALJAZIRA,
                maskedNumber = "3001",
                displayName = "Salary",
            ),
        )
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = accounts.single().displayName,
            maskedNumber = accounts.single().maskedNumber,
            last4Template = { "Account ···$it" },
        )
        assertEquals("Salary", label)
    }

    @Test
    fun cardLabel_lookupByBankAndLast4() {
        val cards = listOf(
            OwnedCardUi(
                bank = Bank.BANK_ALJAZIRA,
                last4 = "7271",
                displayName = "Main CC",
            ),
        )
        val label = DashboardRegistryLabels.resolveCardLabel(
            displayName = cards.single().displayName,
            last4 = cards.single().last4,
            last4Template = { "Card ···$it" },
        )
        assertEquals("Main CC", label)
    }

    @Test
    fun accountLabel_lookupByContainerId() {
        val label = DashboardRegistryLabels.resolveAccountLabel(
            displayName = "Bills",
            maskedNumber = "6810",
            last4Template = { "Account ···$it" },
        )
        assertEquals("Bills", label)
    }
}
