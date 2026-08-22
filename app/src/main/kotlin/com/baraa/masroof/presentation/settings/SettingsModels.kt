package com.baraa.masroof.presentation.settings

import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.application.update.UpdateManifest
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus

data class ManagedCardUi(
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
    val bank: Bank,
    val maskedNumber: String,
    val ownership: OwnershipStatus,
    val displayName: String? = null,
) {
    val displayLabel: String
        get() = displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Account ••${maskedNumber.takeLast(4)}"
}

data class SettingsUiState(
    val loading: Boolean = true,
    val followedCards: List<ManagedCardUi> = emptyList(),
    val unregisteredCards: List<ManagedCardUi> = emptyList(),
    val stoppedCards: List<ManagedCardUi> = emptyList(),
    val followedAccounts: List<ManagedAccountUi> = emptyList(),
    val unregisteredAccounts: List<ManagedAccountUi> = emptyList(),
    val stoppedAccounts: List<ManagedAccountUi> = emptyList(),
    val appVersion: String = "",
    val languageTag: String = "",
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val updating: Boolean = false,
    val stopConfirmCardTarget: ManagedCardUi? = null,
    val stopConfirmAccountTarget: ManagedAccountUi? = null,
    val renameCardTarget: ManagedCardUi? = null,
    val renameAccountTarget: ManagedAccountUi? = null,
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
)

enum class BackupMessage {
    EXPORT_SUCCESS,
    EXPORT_FAILED,
    IMPORT_FAILED,
    IMPORT_INVALID,
}

enum class SmsImportMessage {
    OK,
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

enum class SettingsDestination {
    Hub,
    MyCards,
    MyAccounts,
    About,
}
