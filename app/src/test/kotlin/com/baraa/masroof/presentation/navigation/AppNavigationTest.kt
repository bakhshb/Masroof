package com.baraa.masroof.presentation.navigation

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.settings.SettingsBankSummaryUi
import com.baraa.masroof.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigationTest {
    private val alJazira = Bank("BANK_ALJAZIRA")
    private val rajhi = Bank("BANK_RAJHI")

    private fun summary(bank: Bank) = SettingsBankSummaryUi(
        bank = bank,
        followedAccountCount = 1,
        unregisteredAccountCount = 0,
        stoppedAccountCount = 0,
        followedCardCount = 1,
        unregisteredCardCount = 0,
        stoppedCardCount = 0,
        loanCount = 0,
    )

    @Test
    fun resolveManageSettingsLaunch_singleBank_opensTargetScreenDirectly() {
        val state = SettingsUiState(
            bankSummaries = listOf(summary(alJazira)),
        )

        assertEquals(
            SettingsLaunchRequest(
                destination = SettingsDestination.BankCards(alJazira.id),
                skipBanksList = true,
            ),
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Cards),
        )
        assertEquals(
            SettingsLaunchRequest(
                destination = SettingsDestination.BankAccounts(alJazira.id),
                skipBanksList = true,
            ),
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Accounts),
        )
        assertEquals(
            SettingsLaunchRequest(
                destination = SettingsDestination.BankLoans(alJazira.id),
                skipBanksList = true,
            ),
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Loans),
        )
    }

    @Test
    fun resolveManageSettingsLaunch_multipleBanks_opensBanksList() {
        val state = SettingsUiState(
            bankSummaries = listOf(summary(alJazira), summary(rajhi)),
        )

        assertEquals(
            SettingsLaunchRequest(
                destination = SettingsDestination.Banks,
                skipBanksList = false,
            ),
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Cards),
        )
    }

    @Test
    fun parent_skipsBanksListWhenSingleBankShortcutUsed() {
        val bankHub = SettingsDestination.BankHub(alJazira.id)

        assertEquals(SettingsDestination.Hub, bankHub.parent(skippedBanksList = true))
        assertEquals(SettingsDestination.Banks, bankHub.parent(skippedBanksList = false))
    }

    @Test
    fun resolveBanksEntry_singleBank_skipsBanksList() {
        val state = SettingsUiState(bankSummaries = listOf(summary(alJazira)))

        assertEquals(
            BanksNavigation(
                destination = SettingsDestination.BankHub(alJazira.id),
                skipBanksList = true,
            ),
            resolveBanksEntry(state),
        )
    }

    @Test
    fun resolveNotificationSettingsLaunch_about_returnsAboutDestination() {
        val state = SettingsUiState(bankSummaries = listOf(summary(alJazira)))

        assertEquals(
            SettingsLaunchRequest(SettingsDestination.About),
            resolveNotificationSettingsLaunch(
                com.baraa.masroof.application.notification.NotificationAction.OPEN_SETTINGS_ABOUT,
                state,
            ),
        )
    }

    @Test
    fun resolveNotificationSettingsLaunch_markReadOnly_returnsNull() {
        val state = SettingsUiState(bankSummaries = listOf(summary(alJazira)))

        assertNull(
            resolveNotificationSettingsLaunch(
                com.baraa.masroof.application.notification.NotificationAction.MARK_READ_ONLY,
                state,
            ),
        )
    }
}
