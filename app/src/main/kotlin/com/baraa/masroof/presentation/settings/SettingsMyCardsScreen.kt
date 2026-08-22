package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
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
    onRenameCard: (ManagedCardUi) -> Unit,
    onDismissRenameCard: () -> Unit,
    onSaveCardName: (String) -> Unit,
    onPickCardNetwork: (ManagedCardUi) -> Unit,
    onDismissCardNetwork: () -> Unit,
    onSelectCardNetwork: (com.baraa.masroof.domain.model.CardNetwork?) -> Unit,
    onPickCardRole: (ManagedCardUi) -> Unit,
    onDismissCardRole: () -> Unit,
    onSetPrimaryCard: (ManagedCardUi) -> Unit,
    onSetSupplementaryCard: (ManagedCardUi, String) -> Unit,
    onClearCardRole: (ManagedCardUi) -> Unit,
    onLinkDebitCard: (ManagedCardUi) -> Unit,
    onDismissLinkDebit: () -> Unit,
    onConfirmLinkDebit: (ManagedCardUi, ManagedAccountUi) -> Unit,
    onMarkDebit: (ManagedCardUi) -> Unit,
) {
    SettingsStopConfirmDialog(
        target = state.stopConfirmCardTarget,
        updating = state.updating,
        onDismiss = onDismissStopConfirm,
        onConfirm = onConfirmStopTracking,
    )
    SettingsRenameCardDialog(
        target = state.renameCardTarget,
        updating = state.updating,
        onDismiss = onDismissRenameCard,
        onSave = onSaveCardName,
    )
    SettingsCardNetworkDialog(
        target = state.cardNetworkTarget,
        updating = state.updating,
        onDismiss = onDismissCardNetwork,
        onSelect = onSelectCardNetwork,
    )
    SettingsCardRoleDialog(
        target = state.cardRoleTarget,
        primaryCards = state.followedCards.filter {
            it.cardRole == com.baraa.masroof.domain.model.CardRole.PRIMARY &&
                it.bank == state.cardRoleTarget?.bank
        },
        updating = state.updating,
        onDismiss = onDismissCardRole,
        onSetPrimary = { state.cardRoleTarget?.let(onSetPrimaryCard) },
        onSetSupplementary = { primaryLast4 ->
            state.cardRoleTarget?.let { onSetSupplementaryCard(it, primaryLast4) }
        },
        onClearRole = { state.cardRoleTarget?.let(onClearCardRole) },
    )
    SettingsLinkDebitDialog(
        target = state.linkDebitTarget,
        accounts = state.followedAccounts.filter {
            it.bank == state.linkDebitTarget?.bank
        },
        updating = state.updating,
        onDismiss = onDismissLinkDebit,
        onLink = { account ->
            state.linkDebitTarget?.let { onConfirmLinkDebit(it, account) }
        },
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
                        title = card.displayLabel,
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
                        title = card.displayLabel,
                        endAction = {
                            SettingsStopTrackingButton(
                                onClick = { onRequestStopTracking(card) },
                                enabled = !state.updating,
                                contentDescription = stringResource(R.string.ownership_action_stop_tracking),
                            )
                        },
                        footer = {
                            cardSubtitle(card)?.let { subtitle ->
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            SettingsCardMetadataActions(
                                card = card,
                                enabled = !state.updating,
                                onRename = { onRenameCard(card) },
                                onPickNetwork = { onPickCardNetwork(card) },
                                onPickRole = { onPickCardRole(card) },
                                onLinkDebit = { onLinkDebitCard(card) },
                                onMarkDebit = { onMarkDebit(card) },
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
                        title = card.displayLabel,
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
private fun cardSubtitle(card: ManagedCardUi): String? {
    val parts = buildList {
        card.cardNetwork?.let { add(cardNetworkLabel(it)) }
        card.cardRole?.let { add(cardRoleLabel(it)) }
        card.linkedAccountMaskedNumber?.let {
            add(stringResource(R.string.settings_linked_account_suffix, it.takeLast(4)))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun SettingsCardGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}
