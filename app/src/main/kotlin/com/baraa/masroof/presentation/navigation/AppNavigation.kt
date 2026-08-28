package com.baraa.masroof.presentation.navigation

import com.baraa.masroof.application.notification.NotificationAction
import com.baraa.masroof.presentation.settings.SettingsUiState

internal enum class HomeDestination {
    Dashboard,
    AccountsSummary,
    CardsSummary,
    LoansSummary,
    NotificationCenter,
    Review,
    AllTransactions,
    Settings,
}

internal enum class ManageSettingsTarget {
    Accounts,
    Cards,
    Loans,
}

data class SettingsLaunchRequest(
    val destination: SettingsDestination,
    val skipBanksList: Boolean = false,
)

internal data class BanksNavigation(
    val destination: SettingsDestination,
    val skipBanksList: Boolean,
)

internal fun resolveBanksEntry(state: SettingsUiState): BanksNavigation =
    if (state.bankSummaries.size == 1) {
        BanksNavigation(
            destination = SettingsDestination.BankHub(state.bankSummaries.single().bank.id),
            skipBanksList = true,
        )
    } else {
        BanksNavigation(
            destination = SettingsDestination.Banks,
            skipBanksList = false,
        )
    }

internal fun resolvePendingDestination(
    pending: SettingsDestination,
    state: SettingsUiState,
): SettingsDestination =
    when (pending) {
        SettingsDestination.Banks -> resolveBanksEntry(state).destination
        is SettingsDestination.BankAccounts,
        is SettingsDestination.BankCards,
        is SettingsDestination.BankLoans,
        is SettingsDestination.BankHub,
        -> pending

        SettingsDestination.Hub,
        SettingsDestination.About,
        SettingsDestination.Logs,
        SettingsDestination.DesignCatalog,
        -> pending
    }

internal fun resolveManageSettingsLaunch(
    state: SettingsUiState,
    target: ManageSettingsTarget,
): SettingsLaunchRequest {
    if (state.bankSummaries.size == 1) {
        val bankId = state.bankSummaries.single().bank.id
        val destination = when (target) {
            ManageSettingsTarget.Accounts -> SettingsDestination.BankAccounts(bankId)
            ManageSettingsTarget.Cards -> SettingsDestination.BankCards(bankId)
            ManageSettingsTarget.Loans -> SettingsDestination.BankLoans(bankId)
        }
        return SettingsLaunchRequest(
            destination = destination,
            skipBanksList = true,
        )
    }
    return SettingsLaunchRequest(
        destination = SettingsDestination.Banks,
        skipBanksList = false,
    )
}

internal fun resolveNotificationSettingsLaunch(
    action: NotificationAction,
    state: SettingsUiState,
): SettingsLaunchRequest? =
    when (action) {
        NotificationAction.OPEN_SETTINGS_CARDS ->
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Cards)

        NotificationAction.OPEN_SETTINGS_ACCOUNTS ->
            resolveManageSettingsLaunch(state, ManageSettingsTarget.Accounts)

        NotificationAction.OPEN_SETTINGS_ABOUT ->
            SettingsLaunchRequest(SettingsDestination.About)

        else -> null
    }
