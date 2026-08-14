package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.formatCardLast4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMyCardsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onConfirmOwned: (ManagedCardUi) -> Unit,
    onMarkExternal: (ManagedCardUi) -> Unit,
    onRequestStopTracking: (ManagedCardUi) -> Unit,
    onResumeTracking: (ManagedCardUi) -> Unit,
    onDismissStopConfirm: () -> Unit,
    onConfirmStopTracking: () -> Unit,
) {
    SettingsStopConfirmDialog(
        target = state.stopConfirmCardTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_cards_section),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.cardPayment,
                        bank = card.bank,
                        title = stringResource(
                            R.string.dashboard_credit_card_last4,
                            formatCardLast4(card.last4),
                        ),
                        footer = {
                            CardOwnershipInlinePrompt(
                                enabled = !state.updating,
                                onConfirmOwned = { onConfirmOwned(card) },
                                onMarkExternal = { onMarkExternal(card) },
                            )
                        },
                    )
                }
            }

            if (state.followedCards.isNotEmpty()) {
                SettingsCardGroupTitle(stringResource(R.string.settings_cards_followed))
                state.followedCards.forEach { card ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.cardPayment,
                        bank = card.bank,
                        title = stringResource(
                            R.string.dashboard_credit_card_last4,
                            formatCardLast4(card.last4),
                        ),
                        endAction = {
                            SettingsStopTrackingButton(
                                onClick = { onRequestStopTracking(card) },
                                enabled = !state.updating,
                                contentDescription = stringResource(R.string.ownership_action_stop_tracking),
                            )
                        },
                    )
                }
            }

            if (state.stoppedCards.isNotEmpty()) {
                SettingsCardGroupTitle(stringResource(R.string.settings_cards_stopped))
                state.stoppedCards.forEach { card ->
                    SettingsRegistryItemCard(
                        icon = MasroofIcons.cardPayment,
                        bank = card.bank,
                        title = stringResource(
                            R.string.dashboard_credit_card_last4,
                            formatCardLast4(card.last4),
                        ),
                        endAction = {
                            SettingsResumeTrackingButton(
                                onClick = { onResumeTracking(card) },
                                enabled = !state.updating,
                            )
                        },
                    )
                }
            }

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
fun SettingsStopConfirmDialog(
    target: ManagedCardUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onConfirm, enabled = !updating) {
                Text(stringResource(R.string.settings_stop_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun SettingsCardGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}
