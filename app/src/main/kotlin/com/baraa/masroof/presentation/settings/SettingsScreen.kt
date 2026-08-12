package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onConfirmOwned = viewModel::confirmCardOwned,
        onMarkExternal = viewModel::markCardExternal,
        onRequestStopTracking = viewModel::requestStopTracking,
        onResumeTracking = viewModel::resumeTracking,
        onDismissStopConfirm = viewModel::dismissStopConfirm,
        onConfirmStopTracking = viewModel::confirmStopTracking,
        onReparseStored = viewModel::reparseStoredMessages,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onConfirmOwned: (ManagedCardUi) -> Unit,
    onMarkExternal: (ManagedCardUi) -> Unit,
    onRequestStopTracking: (ManagedCardUi) -> Unit,
    onResumeTracking: (ManagedCardUi) -> Unit,
    onDismissStopConfirm: () -> Unit,
    onConfirmStopTracking: () -> Unit,
    onReparseStored: () -> Unit,
) {
    state.stopConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissStopConfirm,
            icon = {
                Icon(
                    imageVector = MasroofIcons.warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.settings_stop_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_stop_confirm_body,
                        formatCardLast4(target.last4),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmStopTracking, enabled = !state.updating) {
                    Text(stringResource(R.string.settings_stop_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissStopConfirm) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.settings_cards_section),
                icon = MasroofIcons.cardPayment,
            )
            Text(
                stringResource(R.string.settings_cards_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (
                state.followedCards.isEmpty() &&
                state.unregisteredCards.isEmpty() &&
                state.stoppedCards.isEmpty()
            ) {
                Text(
                    stringResource(R.string.settings_cards_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.unregisteredCards.isNotEmpty()) {
                SettingsCardGroupTitle(stringResource(R.string.settings_cards_unregistered))
                state.unregisteredCards.forEach { card ->
                    ManagedCardPanel(card = card) {
                        CardOwnershipInlinePrompt(
                            enabled = !state.updating,
                            onConfirmOwned = { onConfirmOwned(card) },
                            onMarkExternal = { onMarkExternal(card) },
                        )
                    }
                }
            }

            if (state.followedCards.isNotEmpty()) {
                SettingsCardGroupTitle(stringResource(R.string.settings_cards_followed))
                state.followedCards.forEach { card ->
                    ManagedCardPanel(card = card) {
                        IconTextButtonOutlined(
                            onClick = { onRequestStopTracking(card) },
                            icon = MasroofIcons.warning,
                            text = stringResource(R.string.ownership_action_stop_tracking),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.stoppedCards.isNotEmpty()) {
                SettingsCardGroupTitle(stringResource(R.string.settings_cards_stopped))
                state.stoppedCards.forEach { card ->
                    ManagedCardPanel(card = card) {
                        IconTextButton(
                            onClick = { onResumeTracking(card) },
                            enabled = !state.updating,
                            icon = MasroofIcons.success,
                            text = stringResource(R.string.settings_resume_tracking),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionHeader(
                title = stringResource(R.string.settings_data_section),
                icon = MasroofIcons.rescan,
            )
            IconTextButtonOutlined(
                onClick = onReparseStored,
                icon = MasroofIcons.rescan,
                text = if (state.reparsingStored) {
                    stringResource(R.string.dashboard_reparse_stored_running)
                } else {
                    stringResource(R.string.dashboard_reparse_stored)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.settings_reparse_stored_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader(
                title = stringResource(R.string.settings_about_section),
                icon = MasroofIcons.periodHint,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_app_version_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        state.appVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCardGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ManagedCardPanel(
    card: ManagedCardUi,
    actions: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.cardPayment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        bankLabel(card.bank),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.dashboard_credit_card_last4,
                            formatCardLast4(card.last4),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            actions()
        }
    }
}

@Composable
private fun bankLabel(bank: Bank): String =
    if (bank == Bank.BANK_ALJAZIRA) {
        stringResource(R.string.bank_aljazira)
    } else {
        stringResource(R.string.bank_unknown)
    }
