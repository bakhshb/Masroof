package com.baraa.masroof.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.application.backup.BackupImportOutcome
import com.baraa.masroof.application.backup.DatabaseBackupGateway
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.application.theme.ThemePreferencesRepository
import com.baraa.masroof.application.update.AppUpdateService
import com.baraa.masroof.application.update.ApkInstaller
import com.baraa.masroof.application.update.MissingGitHubTokenException
import com.baraa.masroof.application.update.UpdateCheckResult
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val cardRegistryRepository: CardRegistryRepository,
    private val accountRegistryRepository: AccountRegistryRepository,
    private val ownershipConfirmationService: OwnershipConfirmationService,
    private val appLocaleRepository: AppLocaleRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val databaseBackupService: DatabaseBackupGateway,
    private val refreshReviewQueue: suspend () -> Unit,
    private val reparseStoredEvents: suspend () -> Int,
    private val appVersion: String,
    private val appUpdateService: AppUpdateService,
    private val apkInstaller: ApkInstaller,
    private val canInstallPackages: () -> Boolean,
    private val onThemeModeChanged: (ThemeMode) -> Unit = {},
    private val onRequestInstallPermission: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            appVersion = appVersion,
            languageTag = appLocaleRepository.getLanguageTag(),
            themeMode = themePreferencesRepository.getThemeMode(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var pendingImportUri: Uri? = null

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    error = null,
                    languageTag = appLocaleRepository.getLanguageTag(),
                    themeMode = themePreferencesRepository.getThemeMode(),
                    githubTokenConfigured = appUpdateService.hasConfiguredToken(),
                )
            }
            try {
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    fun requestStopTracking(card: ManagedCardUi) {
        _uiState.update { it.copy(stopConfirmCardTarget = card, stopConfirmAccountTarget = null) }
    }

    fun requestStopAccountTracking(account: ManagedAccountUi) {
        _uiState.update { it.copy(stopConfirmAccountTarget = account, stopConfirmCardTarget = null) }
    }

    fun dismissStopConfirm() {
        _uiState.update { it.copy(stopConfirmCardTarget = null, stopConfirmAccountTarget = null) }
    }

    fun confirmStopTracking() {
        val target = _uiState.value.stopConfirmCardTarget ?: return
        dismissStopConfirm()
        updateCardOwnership(target, owned = false)
    }

    fun confirmStopAccountTracking() {
        val target = _uiState.value.stopConfirmAccountTarget ?: return
        dismissStopConfirm()
        updateAccountOwnership(target, owned = false)
    }

    fun confirmCardOwned(card: ManagedCardUi) {
        updateCardOwnership(card, owned = true)
    }

    fun markCardExternal(card: ManagedCardUi) {
        updateCardOwnership(card, owned = false)
    }

    fun resumeTracking(card: ManagedCardUi) {
        updateCardOwnership(card, owned = true)
    }

    fun confirmAccountOwned(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = true)
    }

    fun markAccountExternal(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = false)
    }

    fun resumeAccountTracking(account: ManagedAccountUi) {
        updateAccountOwnership(account, owned = true)
    }

    fun reparseStoredMessages() {
        if (_uiState.value.reparsingStored || _uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(reparsingStored = true, error = null) }
            try {
                reparseStoredEvents()
                refreshReviewQueue()
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(reparsingStored = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(reparsingStored = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    fun clearUpdateMessage() {
        _uiState.update { it.copy(updateMessage = null) }
    }

    fun saveGithubToken(token: String) {
        if (token.isBlank()) return
        appUpdateService.saveToken(token)
        _uiState.update {
            it.copy(
                githubTokenConfigured = true,
                updateMessage = AppUpdateMessage.TOKEN_SAVED,
            )
        }
    }

    fun clearGithubToken() {
        appUpdateService.clearToken()
        _uiState.update {
            it.copy(
                githubTokenConfigured = false,
                updateState = AppUpdateUiState.Idle,
                updateMessage = null,
            )
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (_uiState.value.updateState is AppUpdateUiState.Checking ||
            _uiState.value.updateState is AppUpdateUiState.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateState = AppUpdateUiState.Checking,
                    updateMessage = null,
                    githubTokenConfigured = appUpdateService.hasConfiguredToken(),
                )
            }
            try {
                when (val result = appUpdateService.checkForUpdate().getOrThrow()) {
                    UpdateCheckResult.UpToDate ->
                        _uiState.update {
                            it.copy(
                                updateState = AppUpdateUiState.UpToDate,
                                updateMessage = if (silent) null else AppUpdateMessage.UP_TO_DATE,
                            )
                        }

                    is UpdateCheckResult.UpdateAvailable ->
                        applyAvailableUpdate(result.manifest, silent)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Exception) {
                if (!silent) {
                    val message =
                        if (error is MissingGitHubTokenException) {
                            AppUpdateMessage.TOKEN_REQUIRED
                        } else {
                            AppUpdateMessage.CHECK_FAILED
                        }
                    _uiState.update {
                        it.copy(updateState = AppUpdateUiState.Idle, updateMessage = message)
                    }
                } else {
                    _uiState.update { it.copy(updateState = AppUpdateUiState.Idle) }
                }
            }
        }
    }

    fun downloadUpdate() {
        val state = _uiState.value.updateState
        val manifest =
            when (state) {
                is AppUpdateUiState.Available -> state.manifest
                is AppUpdateUiState.ReadyToInstall -> state.manifest
                else -> return
            }
        if (_uiState.value.updateState is AppUpdateUiState.Downloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    updateState = AppUpdateUiState.Downloading(manifest, 0L, 0L),
                    updateMessage = null,
                )
            }
            try {
                appUpdateService
                    .downloadUpdate(
                        manifest = manifest,
                        onProgress = { bytesRead, totalBytes ->
                            _uiState.update {
                                it.copy(
                                    updateState = AppUpdateUiState.Downloading(
                                        manifest = manifest,
                                        bytesRead = bytesRead,
                                        totalBytes = totalBytes,
                                    ),
                                )
                            }
                        },
                    )
                    .getOrThrow()
                _uiState.update {
                    it.copy(
                        updateState = AppUpdateUiState.ReadyToInstall(manifest),
                        updateMessage = AppUpdateMessage.DOWNLOAD_SUCCESS,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        updateState = AppUpdateUiState.Available(manifest),
                        updateMessage = AppUpdateMessage.DOWNLOAD_FAILED,
                    )
                }
            }
        }
    }

    fun installPendingUpdate() {
        val state = _uiState.value.updateState
        if (state !is AppUpdateUiState.ReadyToInstall) return

        if (!canInstallPackages()) {
            _uiState.update { it.copy(updateMessage = AppUpdateMessage.INSTALL_PERMISSION_REQUIRED) }
            onRequestInstallPermission()
            return
        }

        val apkFile = appUpdateService.updateApkFile(state.manifest)
        viewModelScope.launch {
            try {
                apkInstaller.install(apkFile).getOrThrow()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updateMessage = AppUpdateMessage.INSTALL_FAILED) }
            }
        }
    }

    fun retryInstallAfterPermissionGranted() {
        if (!canInstallPackages()) return
        installPendingUpdate()
    }

    fun setLanguageTag(languageTag: String, onApplied: () -> Unit) {
        val normalized = when (languageTag) {
            AppLocale.TAG_EN -> AppLocale.TAG_EN
            else -> AppLocale.TAG_AR
        }
        if (normalized == appLocaleRepository.getLanguageTag()) return
        appLocaleRepository.setLanguageTag(normalized)
        onApplied()
    }

    fun setThemeMode(mode: ThemeMode) {
        if (mode == themePreferencesRepository.getThemeMode()) return
        themePreferencesRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
        onThemeModeChanged(mode)
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }

    fun offerImport(uri: Uri) {
        if (_uiState.value.exportingBackup || _uiState.value.importingBackup) return
        pendingImportUri = uri
        _uiState.update { it.copy(awaitingImportConfirm = true, backupMessage = null) }
    }

    fun cancelPendingImport() {
        pendingImportUri = null
        _uiState.update { it.copy(awaitingImportConfirm = false) }
    }

    fun confirmPendingImport() {
        val uri = pendingImportUri ?: return
        pendingImportUri = null
        _uiState.update { it.copy(awaitingImportConfirm = false) }
        importBackup(uri)
    }

    fun exportBackup(uri: Uri) {
        if (_uiState.value.exportingBackup || _uiState.value.importingBackup) return
        viewModelScope.launch {
            _uiState.update { it.copy(exportingBackup = true, backupMessage = null, error = null) }
            try {
                databaseBackupService.exportTo(uri).getOrThrow()
                _uiState.update {
                    it.copy(exportingBackup = false, backupMessage = BackupMessage.EXPORT_SUCCESS)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(exportingBackup = false, backupMessage = BackupMessage.EXPORT_FAILED)
                }
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (_uiState.value.exportingBackup || _uiState.value.importingBackup) return
        viewModelScope.launch {
            _uiState.update { it.copy(importingBackup = true, backupMessage = null, error = null) }
            try {
                when (databaseBackupService.importFrom(uri)) {
                    BackupImportOutcome.SuccessNeedsRestart -> {
                        // Process restarts inside the service after a successful restore.
                    }
                    BackupImportOutcome.InvalidPackage ->
                        _uiState.update {
                            it.copy(
                                importingBackup = false,
                                backupMessage = BackupMessage.IMPORT_INVALID,
                            )
                        }
                    BackupImportOutcome.Failed ->
                        _uiState.update {
                            it.copy(
                                importingBackup = false,
                                backupMessage = BackupMessage.IMPORT_FAILED,
                            )
                        }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(importingBackup = false, backupMessage = BackupMessage.IMPORT_FAILED)
                }
            }
        }
    }

    private fun updateCardOwnership(card: ManagedCardUi, owned: Boolean) {
        if (_uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null) }
            try {
                val ref = CardReference(card.bank, card.last4)
                if (owned) {
                    ownershipConfirmationService.confirmCardOwned(ref)
                } else {
                    ownershipConfirmationService.markCardExternal(ref)
                }
                refreshReviewQueue()
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(updating = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updating = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    private fun updateAccountOwnership(account: ManagedAccountUi, owned: Boolean) {
        if (_uiState.value.updating) return
        viewModelScope.launch {
            _uiState.update { it.copy(updating = true, error = null) }
            try {
                val ref = AccountReference(account.bank, account.maskedNumber)
                if (owned) {
                    ownershipConfirmationService.confirmAccountOwned(ref)
                } else {
                    ownershipConfirmationService.markAccountExternal(ref)
                }
                refreshReviewQueue()
                applyRegistries(
                    cards = cardRegistryRepository.listAll(),
                    accounts = accountRegistryRepository.listAll(),
                )
                _uiState.update { it.copy(updating = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                _uiState.update { it.copy(updating = false, error = SettingsError.UPDATE_FAILED) }
            }
        }
    }

    private fun applyAvailableUpdate(
        manifest: com.baraa.masroof.application.update.UpdateManifest,
        silent: Boolean,
    ) {
        val downloaded = appUpdateService.updateApkFile(manifest)
        val nextState =
            if (
                downloaded.exists() &&
                com.baraa.masroof.application.update.ApkIntegrityVerifier.matches(downloaded, manifest.sha256)
            ) {
                AppUpdateUiState.ReadyToInstall(manifest)
            } else {
                if (downloaded.exists()) downloaded.delete()
                AppUpdateUiState.Available(manifest)
            }
        _uiState.update {
            it.copy(
                updateState = nextState,
                updateMessage = if (silent || nextState is AppUpdateUiState.ReadyToInstall) {
                    AppUpdateMessage.UPDATE_AVAILABLE
                } else {
                    AppUpdateMessage.UPDATE_AVAILABLE
                },
            )
        }
    }

    private fun applyRegistries(
        cards: List<com.baraa.masroof.domain.model.CardRegistryEntry>,
        accounts: List<com.baraa.masroof.domain.model.AccountRegistryEntry>,
    ) {
        val cardItems = cards
            .filter { it.bank != Bank.UNKNOWN }
            .map { ManagedCardUi(bank = it.bank, last4 = it.last4, ownership = it.ownership) }
            .sortedBy { it.last4 }
        val accountItems = accounts
            .filter { it.bank != Bank.UNKNOWN }
            .map { ManagedAccountUi(bank = it.bank, maskedNumber = it.maskedNumber, ownership = it.ownership) }
            .sortedBy { it.maskedNumber }
        _uiState.update {
            it.copy(
                loading = false,
                followedCards = cardItems.filter { card -> card.ownership == OwnershipStatus.OWNED },
                unregisteredCards = cardItems.filter { card -> card.ownership == OwnershipStatus.UNKNOWN },
                stoppedCards = cardItems.filter { card -> card.ownership == OwnershipStatus.EXTERNAL },
                followedAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.OWNED },
                unregisteredAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.UNKNOWN },
                stoppedAccounts = accountItems.filter { account -> account.ownership == OwnershipStatus.EXTERNAL },
                error = null,
            )
        }
    }
}
