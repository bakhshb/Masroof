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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.baraa.masroof.presentation.common.BackNavigationIcon
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
    onRequestSmsPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Hub) }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination != null) {
            destination = pendingDestination
            onPendingDestinationConsumed()
        }
    }

    BackHandler(enabled = destination != SettingsDestination.Hub) {
        destination = SettingsDestination.Hub
    }

    when (destination) {
        SettingsDestination.Hub -> SettingsHubScreen(
            state = state,
            reviewRequiredCount = reviewRequiredCount,
            onBack = onBack,
            onOpenMyCards = { destination = SettingsDestination.MyCards },
            onOpenMyAccounts = { destination = SettingsDestination.MyAccounts },
            onOpenIgnoredMessages = { destination = SettingsDestination.IgnoredMessages },
            onOpenReview = onOpenReview,
            onOpenAbout = { destination = SettingsDestination.About },
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

        SettingsDestination.MyCards -> SettingsMyCardsScreen(
            state = state,
            onBack = { destination = SettingsDestination.Hub },
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

        SettingsDestination.IgnoredMessages -> SettingsIgnoredMessagesScreen(
            state = state,
            onBack = { destination = SettingsDestination.Hub },
            onRestore = viewModel::restoreIgnoredMessage,
            onClearRestoreMessage = viewModel::clearRestoreMessage,
        )

        SettingsDestination.MyAccounts -> SettingsMyAccountsScreen(
            state = state,
            onBack = { destination = SettingsDestination.Hub },
            onConfirmOwned = viewModel::confirmAccountOwned,
            onMarkExternal = viewModel::markAccountExternal,
            onRequestStopTracking = viewModel::requestStopAccountTracking,
            onResumeTracking = viewModel::resumeAccountTracking,
            onDismissStopConfirm = viewModel::dismissStopConfirm,
            onConfirmStopTracking = viewModel::confirmStopAccountTracking,
            onRenameAccount = viewModel::openRenameAccount,
            onDismissRenameAccount = viewModel::dismissRenameAccount,
            onSaveAccountName = viewModel::saveAccountDisplayName,
        )

        SettingsDestination.About -> SettingsAboutScreen(
            appVersion = state.appVersion,
            githubTokenConfigured = state.githubTokenConfigured,
            updateState = state.updateState,
            updateMessage = state.updateMessage,
            onBack = { destination = SettingsDestination.Hub },
            onSaveGithubToken = viewModel::saveGithubToken,
            onClearGithubToken = viewModel::clearGithubToken,
            onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
            onDownloadUpdate = viewModel::downloadUpdate,
            onInstallUpdate = viewModel::installPendingUpdate,
            onClearUpdateMessage = viewModel::clearUpdateMessage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubScreen(
    state: SettingsUiState,
    reviewRequiredCount: Int,
    onBack: () -> Unit,
    onOpenMyCards: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    onOpenIgnoredMessages: () -> Unit,
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
                icon = MasroofIcons.cardPayment,
                title = stringResource(R.string.settings_cards_section),
                subtitle = cardsHubSubtitle(state),
                onClick = onOpenMyCards,
            )

            SettingsNavRow(
                icon = MasroofIcons.externalIn,
                title = stringResource(R.string.settings_accounts_section),
                subtitle = accountsHubSubtitle(state),
                onClick = onOpenMyAccounts,
            )

            SettingsNavRow(
                icon = MasroofIcons.notifications,
                title = stringResource(R.string.settings_hub_review_title),
                subtitle = reviewHubSubtitle(reviewRequiredCount),
                badgeCount = reviewRequiredCount.takeIf { it > 0 },
                onClick = onOpenReview,
            )

            if (state.ignoredMessages.isNotEmpty()) {
                SettingsNavRow(
                    icon = MasroofIcons.recentTransactions,
                    title = stringResource(R.string.settings_ignored_messages_title),
                    subtitle = stringResource(
                        R.string.settings_ignored_messages_subtitle,
                        state.ignoredMessages.size,
                    ),
                    badgeCount = state.ignoredMessages.size,
                    onClick = onOpenIgnoredMessages,
                )
            }

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
private fun cardsHubSubtitle(state: SettingsUiState): String {
    val followed = state.followedCards.size
    val unregistered = state.unregisteredCards.size
    val stopped = state.stoppedCards.size
    return when {
        followed == 0 && unregistered == 0 && stopped == 0 ->
            stringResource(R.string.settings_hub_cards_subtitle_none)

        unregistered > 0 ->
            stringResource(R.string.settings_hub_cards_subtitle, followed, unregistered)

        else ->
            stringResource(R.string.settings_hub_cards_subtitle_followed_only, followed)
    }
}

@Composable
private fun accountsHubSubtitle(state: SettingsUiState): String {
    val followed = state.followedAccounts.size
    val unregistered = state.unregisteredAccounts.size
    val stopped = state.stoppedAccounts.size
    return when {
        followed == 0 && unregistered == 0 && stopped == 0 ->
            stringResource(R.string.settings_hub_accounts_subtitle_none)

        unregistered > 0 ->
            stringResource(R.string.settings_hub_accounts_subtitle, followed, unregistered)

        else ->
            stringResource(R.string.settings_hub_accounts_subtitle_followed_only, followed)
    }
}

@Composable
private fun reviewHubSubtitle(reviewRequiredCount: Int): String =
    if (reviewRequiredCount > 0) {
        stringResource(R.string.settings_hub_review_subtitle_count, reviewRequiredCount)
    } else {
        stringResource(R.string.settings_hub_review_subtitle_none)
    }
