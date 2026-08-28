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

/**
 * Open Settings on [destination], replacing the settings stack.
 *
 * Back from that screen leaves Settings and returns to the caller of
 * `openSettings`. Taps after landing push onto the stack and pop normally.
 */
data class SettingsLaunchRequest(
    val destination: SettingsDestination,
)

internal fun resolveBanksEntry(state: SettingsUiState): SettingsDestination =
    if (state.bankSummaries.size == 1) {
        SettingsDestination.BankHub(state.bankSummaries.single().bank.id)
    } else {
        SettingsDestination.Banks
    }

internal fun resolvePendingDestination(
    pending: SettingsDestination,
    state: SettingsUiState,
): SettingsDestination =
    when (pending) {
        SettingsDestination.Banks -> resolveBanksEntry(state)
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
        return SettingsLaunchRequest(destination)
    }
    return SettingsLaunchRequest(SettingsDestination.Banks)
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
