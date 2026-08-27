package com.baraa.masroof.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.SectionHeader

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    reviewRequiredCount: Int,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
    pendingDestination: SettingsDestination? = null,
    onPendingDestinationConsumed: () -> Unit = {},
    onLocaleChanged: () -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onRequestExportLogs: () -> Unit,
    onRequestSmsPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    var destinationRoute by rememberSaveable { mutableStateOf(SettingsDestination.Hub.encode()) }
    var skippedBanksList by rememberSaveable { mutableStateOf(false) }
    val destination = decodeSettingsDestination(destinationRoute)

    fun bankHubBack(): SettingsDestination = destination.parent(skippedBanksList)

    fun navigateTo(next: SettingsDestination, skipBanksList: Boolean = false) {
        destinationRoute = next.encode()
        skippedBanksList = when {
            skipBanksList -> true
            next == SettingsDestination.Banks || next == SettingsDestination.Hub -> false
            else -> skippedBanksList
        }
    }

    fun openBanksEntry() {
        val entry = resolveBanksEntry(state)
        navigateTo(entry.destination, skipBanksList = entry.skipBanksList)
    }

    LaunchedEffect(pendingDestination, state.loading, state.bankSummaries) {
        if (pendingDestination == null) return@LaunchedEffect
        if (pendingDestination == SettingsDestination.Banks && state.loading) return@LaunchedEffect
        when (pendingDestination) {
            SettingsDestination.Banks -> {
                val entry = resolveBanksEntry(state)
                navigateTo(entry.destination, skipBanksList = entry.skipBanksList)
            }
            else -> navigateTo(resolvePendingDestination(pendingDestination, state))
        }
        onPendingDestinationConsumed()
    }

    LaunchedEffect(destination) {
        if (destination == SettingsDestination.About || destination == SettingsDestination.Logs) {
            viewModel.refreshLogs()
        }
    }

    BackHandler(enabled = destination != SettingsDestination.Hub) {
        navigateTo(destination.parent(skippedBanksList))
    }

    val logEntries by viewModel.logEntries.collectAsState()
    val logErrorCount = logEntries.count { it.level == com.baraa.masroof.application.logging.AppLogLevel.ERROR }

    when (val current = destination) {
        SettingsDestination.Hub -> SettingsHubScreen(
            state = state,
            reviewRequiredCount = reviewRequiredCount,
            onBack = onBack,
            onOpenBanks = { openBanksEntry() },
            onOpenReview = onOpenReview,
            onOpenAbout = { navigateTo(SettingsDestination.About) },
            onReparseStored = viewModel::reparseStoredMessages,
            onImportSms = viewModel::importSmsFromPhone,
            onClearSmsImportMessage = viewModel::clearSmsImportMessage,
            onRequestSmsPermission = onRequestSmsPermission,
            onOpenAppSettings = onOpenAppSettings,
            onSelectLanguage = { tag ->
                viewModel.setLanguageTag(tag, onLocaleChanged)
            },
            onSelectTheme = viewModel::setThemeMode,
            onRequestExport = onRequestExport,
            onRequestImport = onRequestImport,
            onConfirmPendingImport = viewModel::confirmPendingImport,
            onCancelPendingImport = viewModel::cancelPendingImport,
            onClearBackupMessage = viewModel::clearBackupMessage,
        )

        SettingsDestination.Banks -> SettingsBanksScreen(
            state = state,
            onBack = { navigateTo(SettingsDestination.Hub) },
            onOpenBank = { summary -> navigateTo(SettingsDestination.BankHub(summary.bank.id)) },
        )

        is SettingsDestination.BankHub -> {
            val bank = Bank(current.bankId)
            val summary = state.bankSummary(current.bankId)
            when {
                summary != null -> SettingsBankScreen(
                    bank = bank,
                    summary = summary,
                    onBack = { navigateTo(bankHubBack()) },
                    onOpenAccounts = { navigateTo(SettingsDestination.BankAccounts(current.bankId)) },
                    onOpenCards = { navigateTo(SettingsDestination.BankCards(current.bankId)) },
                    onOpenLoans = { navigateTo(SettingsDestination.BankLoans(current.bankId)) },
                )

                state.loading -> SettingsBankHubLoadingScreen(
                    bank = bank,
                    onBack = { navigateTo(bankHubBack()) },
                )

                else -> {
                    LaunchedEffect(current.bankId) {
                        navigateTo(SettingsDestination.Banks)
                    }
                    SettingsBankHubLoadingScreen(
                        bank = bank,
                        onBack = { navigateTo(bankHubBack()) },
                    )
                }
            }
        }

        is SettingsDestination.BankAccounts -> SettingsBankAccountsScreen(
            bank = Bank(current.bankId),
            state = state,
            onBack = { navigateTo(SettingsDestination.BankHub(current.bankId)) },
            onConfirmOwned = viewModel::confirmAccountOwned,
            onMarkExternal = viewModel::markAccountExternal,
            onRequestStopTracking = viewModel::requestStopAccountTracking,
            onResumeTracking = viewModel::resumeAccountTracking,
            onDismissStopConfirm = viewModel::dismissStopConfirm,
            onConfirmStopTracking = viewModel::confirmStopAccountTracking,
            onRenameAccount = viewModel::openRenameAccount,
            onDismissRenameAccount = viewModel::dismissRenameAccount,
            onSaveAccountName = viewModel::saveAccountDisplayName,
            onPickAccountType = viewModel::openAccountTypePicker,
            onDismissAccountType = viewModel::dismissAccountTypePicker,
            onSelectAccountType = viewModel::setAccountTypeFromPicker,
        )

        is SettingsDestination.BankCards -> SettingsBankCardsScreen(
            bank = Bank(current.bankId),
            state = state,
            onBack = { navigateTo(SettingsDestination.BankHub(current.bankId)) },
            onConfirmOwned = viewModel::confirmCardOwned,
            onMarkExternal = viewModel::markCardExternal,
            onRequestStopTracking = viewModel::requestStopTracking,
            onResumeTracking = viewModel::resumeTracking,
            onDismissStopConfirm = viewModel::dismissStopConfirm,
            onConfirmStopTracking = viewModel::confirmStopTracking,
            onRenameCard = viewModel::openRenameCard,
            onDismissRenameCard = viewModel::dismissRenameCard,
            onSaveCardName = viewModel::saveCardDisplayName,
            onPickCardNetwork = viewModel::openCardNetworkPicker,
            onDismissCardNetwork = viewModel::dismissCardNetworkPicker,
            onSelectCardNetwork = viewModel::setCardNetwork,
            onPickCardRole = viewModel::openCardRolePicker,
            onDismissCardRole = viewModel::dismissCardRolePicker,
            onSetPrimaryCard = viewModel::setPrimaryCard,
            onSetSupplementaryCard = viewModel::setSupplementaryCard,
            onClearCardRole = viewModel::clearCardRole,
            onLinkDebitCard = viewModel::openLinkDebitCard,
            onDismissLinkDebit = viewModel::dismissLinkDebitCard,
            onConfirmLinkDebit = viewModel::linkDebitToAccount,
            onMarkDebit = viewModel::markCardAsDebit,
        )

        is SettingsDestination.BankLoans -> SettingsBankLoansScreen(
            bank = Bank(current.bankId),
            state = state,
            onBack = { navigateTo(SettingsDestination.BankHub(current.bankId)) },
        )

        SettingsDestination.About -> SettingsAboutScreen(
            appVersion = state.appVersion,
            githubTokenConfigured = state.githubTokenConfigured,
            updateState = state.updateState,
            updateMessage = state.updateMessage,
            logErrorCount = logErrorCount,
            onBack = { navigateTo(SettingsDestination.Hub) },
            onOpenLogs = { navigateTo(SettingsDestination.Logs) },
            onSaveGithubToken = viewModel::saveGithubToken,
            onClearGithubToken = viewModel::clearGithubToken,
            onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
            onDownloadUpdate = viewModel::downloadUpdate,
            onInstallUpdate = viewModel::installPendingUpdate,
            onClearUpdateMessage = viewModel::clearUpdateMessage,
        )

        SettingsDestination.Logs -> SettingsLogsScreen(
            viewModel = viewModel,
            onBack = { navigateTo(SettingsDestination.About) },
            onRequestExport = onRequestExportLogs,
        )
    }
}

private data class BanksNavigation(
    val destination: SettingsDestination,
    val skipBanksList: Boolean,
)

private fun resolveBanksEntry(state: SettingsUiState): BanksNavigation =
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

private fun resolvePendingDestination(
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
        -> pending
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBankHubLoadingScreen(
    bank: Bank,
    onBack: () -> Unit,
) {
    MasroofSecondaryScaffold(
        title = settingsBankLabel(bank),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubScreen(
    state: SettingsUiState,
    reviewRequiredCount: Int,
    onBack: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAbout: () -> Unit,
    onReparseStored: () -> Unit,
    onImportSms: () -> Unit,
    onClearSmsImportMessage: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onRequestExport: () -> Unit,
    onRequestImport: () -> Unit,
    onConfirmPendingImport: () -> Unit,
    onCancelPendingImport: () -> Unit,
    onClearBackupMessage: () -> Unit,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    if (showLanguageDialog) {
        SettingsLanguageDialog(
            selectedLanguageTag = state.languageTag,
            onDismiss = { showLanguageDialog = false },
            onSelectLanguage = { tag ->
                showLanguageDialog = false
                onSelectLanguage(tag)
            },
        )
    }
    if (showThemeDialog) {
        SettingsThemeDialog(
            selectedMode = state.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelectMode = { mode ->
                showThemeDialog = false
                onSelectTheme(mode)
            },
        )
    }
    if (state.awaitingImportConfirm) {
        AlertDialog(
            onDismissRequest = onCancelPendingImport,
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmPendingImport) {
                    Text(stringResource(R.string.settings_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelPendingImport) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    state.backupMessage?.let { message ->
        val text = when (message) {
            BackupMessage.EXPORT_SUCCESS -> stringResource(R.string.settings_export_success)
            BackupMessage.EXPORT_FAILED -> stringResource(R.string.settings_export_failed)
            BackupMessage.IMPORT_FAILED -> stringResource(R.string.settings_import_failed)
            BackupMessage.IMPORT_INVALID -> stringResource(R.string.settings_import_invalid)
        }
        AlertDialog(
            onDismissRequest = onClearBackupMessage,
            title = { Text(stringResource(R.string.settings_data_section)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = onClearBackupMessage) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    state.smsImportMessage?.let { message ->
        val text = when (message) {
            SmsImportMessage.OK -> stringResource(R.string.dashboard_rescan_ok)
            SmsImportMessage.ALREADY_UP_TO_DATE -> stringResource(R.string.dashboard_rescan_already_up_to_date)
            SmsImportMessage.NEEDS_REVIEW -> stringResource(R.string.dashboard_rescan_needs_review)
            SmsImportMessage.PERMISSION_DENIED -> stringResource(R.string.settings_import_sms_permission_denied)
            SmsImportMessage.NO_MESSAGES -> stringResource(R.string.dashboard_rescan_no_messages)
            SmsImportMessage.NO_BANK_SMS -> stringResource(R.string.dashboard_rescan_no_bank_sms)
            SmsImportMessage.NO_TRANSACTIONS -> stringResource(R.string.dashboard_rescan_no_transactions)
            SmsImportMessage.FAILED -> stringResource(R.string.dashboard_rescan_failed)
        }
        AlertDialog(
            onDismissRequest = onClearSmsImportMessage,
            title = { Text(stringResource(R.string.settings_import_sms_title)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    if (message == SmsImportMessage.PERMISSION_DENIED) {
                        onRequestSmsPermission()
                    }
                    onClearSmsImportMessage()
                }) {
                    Text(
                        if (message == SmsImportMessage.PERMISSION_DENIED) {
                            stringResource(R.string.dashboard_sms_permission_grant)
                        } else {
                            stringResource(R.string.settings_cancel)
                        },
                    )
                }
            },
            dismissButton = {
                if (message == SmsImportMessage.PERMISSION_DENIED) {
                    TextButton(onClick = onClearSmsImportMessage) {
                        Text(stringResource(R.string.settings_cancel))
                    }
                }
            },
        )
    }

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        if (state.loading) {
            Column(
                modifier = contentModifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@MasroofSecondaryScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                icon = MasroofIcons.moneyMovement,
                title = stringResource(R.string.settings_banks_section),
                subtitle = banksHubSubtitle(state),
                onClick = onOpenBanks,
            )

            SettingsNavRow(
                icon = MasroofIcons.notifications,
                title = stringResource(R.string.settings_hub_review_title),
                subtitle = reviewHubSubtitle(reviewRequiredCount),
                badgeCount = reviewRequiredCount.takeIf { it > 0 },
                onClick = onOpenReview,
            )

            SettingsNavRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language_title),
                subtitle = languageSubtitle(state.languageTag),
                onClick = { showLanguageDialog = true },
            )

            SettingsNavRow(
                icon = MasroofIcons.theme,
                title = stringResource(R.string.settings_theme_title),
                subtitle = themeSubtitle(state.themeMode),
                onClick = { showThemeDialog = true },
            )

            SectionHeader(
                title = stringResource(R.string.settings_data_section),
                icon = MasroofIcons.rescan,
            )

            if (!state.smsPermissionGranted) {
                com.baraa.masroof.presentation.common.SmsPermissionNotice(
                    onRequestPermission = onRequestSmsPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }

            SettingsReparseRow(
                title = stringResource(R.string.settings_import_sms_title),
                subtitle = stringResource(R.string.settings_import_sms_subtitle),
                detail = null,
                icon = MasroofIcons.externalIn,
                actionIcon = MasroofIcons.rescan,
                running = state.importingSms,
                enabled = !state.importingSms && !state.reparsingStored && !state.updating &&
                    !state.exportingBackup && !state.importingBackup,
                onRefresh = onImportSms,
            )

            SettingsReparseRow(
                title = stringResource(R.string.settings_reparse_title),
                subtitle = stringResource(R.string.settings_reparse_stored_hint),
                detail = stringResource(R.string.settings_reparse_stored_example),
                icon = MasroofIcons.rescan,
                actionIcon = MasroofIcons.retry,
                running = state.reparsingStored,
                enabled = !state.reparsingStored && !state.updating &&
                    !state.exportingBackup && !state.importingBackup,
                onRefresh = onReparseStored,
            )

            SettingsNavRow(
                icon = MasroofIcons.export,
                title = stringResource(R.string.settings_export_title),
                subtitle = stringResource(R.string.settings_export_subtitle),
                onClick = onRequestExport,
                enabled = !state.exportingBackup && !state.importingBackup && !state.reparsingStored,
            )

            SettingsNavRow(
                icon = MasroofIcons.importBackup,
                title = stringResource(R.string.settings_import_title),
                subtitle = stringResource(R.string.settings_import_subtitle),
                onClick = onRequestImport,
                enabled = !state.exportingBackup && !state.importingBackup && !state.reparsingStored,
            )

            if (state.exportingBackup || state.importingBackup) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            SettingsNavRow(
                icon = MasroofIcons.periodHint,
                title = stringResource(R.string.settings_about_section),
                subtitle = stringResource(R.string.settings_about_subtitle, state.appVersion),
                onClick = onOpenAbout,
            )

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun languageSubtitle(languageTag: String): String =
    if (AppLocale.isEnglish(languageTag)) {
        stringResource(R.string.settings_language_english)
    } else {
        stringResource(R.string.settings_language_arabic)
    }

@Composable
private fun themeSubtitle(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }

@Composable
private fun banksHubSubtitle(state: SettingsUiState): String {
    val bankCount = state.bankSummaries.size
    val unregistered = state.bankSummaries.sumOf { it.unregisteredCount }
    return when {
        bankCount == 0 -> stringResource(R.string.settings_hub_banks_subtitle_none)
        unregistered > 0 -> stringResource(R.string.settings_hub_banks_subtitle, bankCount, unregistered)
        else -> stringResource(R.string.settings_hub_banks_subtitle_banks_only, bankCount)
    }
}

@Composable
private fun reviewHubSubtitle(reviewRequiredCount: Int): String =
    if (reviewRequiredCount > 0) {
        stringResource(R.string.settings_hub_review_subtitle_count, reviewRequiredCount)
    } else {
        stringResource(R.string.settings_hub_review_subtitle_none)
    }
