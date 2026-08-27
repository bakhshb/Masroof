package com.baraa.masroof.presentation.settings

import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.application.update.UpdateManifest
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.OwnershipStatus

data class ManagedCardUi(
    val id: String,
    val bank: Bank,
    val last4: String,
    val ownership: OwnershipStatus,
    val displayName: String? = null,
    val cardNetwork: CardNetwork? = null,
    val cardType: CardType? = null,
    val cardRole: CardRole? = null,
    val parentCardLast4: String? = null,
    val linkedAccountMaskedNumber: String? = null,
) {
    val displayLabel: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: when (cardRole) {
                CardRole.PRIMARY -> "Primary ••$last4"
                CardRole.SUPPLEMENTARY -> "Additional ••$last4"
                CardRole.STANDALONE, null -> when (cardType) {
                    CardType.DEBIT -> "Mada ••$last4"
                    CardType.CREDIT -> "Credit ••$last4"
                    null -> "••$last4"
                }
            }
}

data class ManagedAccountUi(
    val id: String,
    val bank: Bank,
    val maskedNumber: String,
    val ownership: OwnershipStatus,
    val displayName: String? = null,
    val accountType: AccountType = AccountType.CURRENT,
) {
    val displayLabel: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Account ••${maskedNumber.takeLast(4)}"
}

data class SettingsBankTreeUi(
    val bank: Bank,
    val currentAccountNodes: List<SettingsCurrentAccountNodeUi> = emptyList(),
    val savingsAccounts: List<ManagedAccountUi>,
    val walletAccounts: List<ManagedAccountUi>,
    val creditCards: List<ManagedCardUi>,
    val unlinkedDebitCards: List<ManagedCardUi> = emptyList(),
    val loans: List<ManagedLoanUi> = emptyList(),
)

data class SettingsCurrentAccountNodeUi(
    val account: ManagedAccountUi,
    val debitCards: List<ManagedCardUi>,
)

data class ManagedLoanUi(
    val id: String,
    val bank: Bank,
    val loanType: com.baraa.masroof.domain.model.LoanType,
    val ownership: OwnershipStatus,
    val displayName: String? = null,
)

data class SettingsBankSummaryUi(
    val bank: Bank,
    val accountCount: Int,
    val cardCount: Int,
    val loanCount: Int,
    val unregisteredAccountCount: Int,
    val unregisteredCardCount: Int,
) {
    val hasContent: Boolean
        get() = accountCount > 0 || cardCount > 0 || loanCount > 0

    val unregisteredCount: Int
        get() = unregisteredAccountCount + unregisteredCardCount
}

data class SettingsUiState(
    val loading: Boolean = true,
    val followedCards: List<ManagedCardUi> = emptyList(),
    val unregisteredCards: List<ManagedCardUi> = emptyList(),
    val stoppedCards: List<ManagedCardUi> = emptyList(),
    val followedAccounts: List<ManagedAccountUi> = emptyList(),
    val unregisteredAccounts: List<ManagedAccountUi> = emptyList(),
    val stoppedAccounts: List<ManagedAccountUi> = emptyList(),
    val loans: List<ManagedLoanUi> = emptyList(),
    val bankTrees: List<SettingsBankTreeUi> = emptyList(),
    val bankSummaries: List<SettingsBankSummaryUi> = emptyList(),
    val appVersion: String = "",
    val languageTag: String = "",
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val updating: Boolean = false,
    val stopConfirmCardTarget: ManagedCardUi? = null,
    val stopConfirmAccountTarget: ManagedAccountUi? = null,
    val renameCardTarget: ManagedCardUi? = null,
    val renameAccountTarget: ManagedAccountUi? = null,
    val accountTypeTarget: ManagedAccountUi? = null,
    val cardNetworkTarget: ManagedCardUi? = null,
    val cardRoleTarget: ManagedCardUi? = null,
    val linkDebitTarget: ManagedCardUi? = null,
    val reparsingStored: Boolean = false,
    val importingSms: Boolean = false,
    val smsImportMessage: SmsImportMessage? = null,
    val smsPermissionGranted: Boolean = true,
    val exportingBackup: Boolean = false,
    val importingBackup: Boolean = false,
    val awaitingImportConfirm: Boolean = false,
    val backupMessage: BackupMessage? = null,
    val error: SettingsError? = null,
    val githubTokenConfigured: Boolean = false,
    val updateState: AppUpdateUiState = AppUpdateUiState.Idle,
    val updateMessage: AppUpdateMessage? = null,
    val exportingLogs: Boolean = false,
    val logMessage: LogMessage? = null,
)

enum class LogMessage {
    EXPORT_SUCCESS,
    EXPORT_FAILED,
    CLEARED,
}

enum class BackupMessage {
    EXPORT_SUCCESS,
    EXPORT_FAILED,
    IMPORT_FAILED,
    IMPORT_INVALID,
}

enum class SmsImportMessage {
    OK,
    ALREADY_UP_TO_DATE,
    NEEDS_REVIEW,
    PERMISSION_DENIED,
    NO_MESSAGES,
    NO_BANK_SMS,
    NO_TRANSACTIONS,
    FAILED,
}

enum class SettingsError {
    UPDATE_FAILED,
}

enum class AppUpdateMessage {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOAD_SUCCESS,
    TOKEN_SAVED,
    TOKEN_REQUIRED,
    AUTH_FAILED,
    CHECK_FAILED,
    DOWNLOAD_FAILED,
    INSTALL_FAILED,
    INSTALL_PERMISSION_REQUIRED,
}

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    data object Checking : AppUpdateUiState

    data object UpToDate : AppUpdateUiState

    data class Available(
        val manifest: UpdateManifest,
    ) : AppUpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : AppUpdateUiState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
    ) : AppUpdateUiState
}

sealed interface SettingsDestination {
    data object Hub : SettingsDestination

    data object Banks : SettingsDestination

    data class BankHub(
        val bankId: String,
    ) : SettingsDestination

    data class BankAccounts(
        val bankId: String,
    ) : SettingsDestination

    data class BankCards(
        val bankId: String,
    ) : SettingsDestination

    data class BankLoans(
        val bankId: String,
    ) : SettingsDestination

    data object About : SettingsDestination

    data object Logs : SettingsDestination
}

fun SettingsDestination.encode(): String =
    when (this) {
        SettingsDestination.Hub -> "hub"
        SettingsDestination.Banks -> "banks"
        is SettingsDestination.BankHub -> "bank:$bankId"
        is SettingsDestination.BankAccounts -> "bank:$bankId:accounts"
        is SettingsDestination.BankCards -> "bank:$bankId:cards"
        is SettingsDestination.BankLoans -> "bank:$bankId:loans"
        SettingsDestination.About -> "about"
        SettingsDestination.Logs -> "logs"
    }

fun decodeSettingsDestination(encoded: String): SettingsDestination {
    if (encoded == "hub") return SettingsDestination.Hub
    if (encoded == "banks") return SettingsDestination.Banks
    if (encoded == "about") return SettingsDestination.About
    if (encoded == "logs") return SettingsDestination.Logs
    if (encoded.startsWith("bank:")) {
        val parts = encoded.removePrefix("bank:").split(":")
        val bankId = parts.firstOrNull().orEmpty()
        return when (parts.getOrNull(1)) {
            "accounts" -> SettingsDestination.BankAccounts(bankId)
            "cards" -> SettingsDestination.BankCards(bankId)
            "loans" -> SettingsDestination.BankLoans(bankId)
            else -> SettingsDestination.BankHub(bankId)
        }
    }
    return SettingsDestination.Hub
}

fun SettingsDestination.parent(): SettingsDestination =
    when (this) {
        is SettingsDestination.BankAccounts -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankCards -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankLoans -> SettingsDestination.BankHub(bankId)
        is SettingsDestination.BankHub -> SettingsDestination.Banks
        SettingsDestination.Banks -> SettingsDestination.Hub
        SettingsDestination.Logs -> SettingsDestination.About
        SettingsDestination.About,
        SettingsDestination.Hub,
        -> SettingsDestination.Hub
    }

fun SettingsUiState.bankSummary(bankId: String): SettingsBankSummaryUi? =
    bankSummaries.firstOrNull { it.bank.id == bankId }

fun SettingsUiState.bankTree(bankId: String): SettingsBankTreeUi? =
    bankTrees.firstOrNull { it.bank.id == bankId }
