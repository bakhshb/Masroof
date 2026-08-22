package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.presentation.common.formatCardLast4

@Composable
fun SettingsRenameCardDialog(
    target: ManagedCardUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    target ?: return
    var name by rememberSaveable(target.last4) { mutableStateOf(target.displayName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_card_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !updating) {
                Text(stringResource(R.string.settings_save))
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
fun SettingsRenameAccountDialog(
    target: ManagedAccountUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    target ?: return
    var name by rememberSaveable(target.maskedNumber) { mutableStateOf(target.displayName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_account_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = !updating) {
                Text(stringResource(R.string.settings_save))
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
fun SettingsCardNetworkDialog(
    target: ManagedCardUi?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CardNetwork?) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_card_network_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardNetwork.entries.filter { it != CardNetwork.UNKNOWN }.forEach { network ->
                    Text(
                        text = cardNetworkLabel(network),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !updating) { onSelect(network) }
                            .padding(vertical = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_card_network_clear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onSelect(null) }
                        .padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsCardRoleDialog(
    target: ManagedCardUi?,
    primaryCards: List<ManagedCardUi>,
    updating: Boolean,
    onDismiss: () -> Unit,
    onSetPrimary: () -> Unit,
    onSetSupplementary: (String) -> Unit,
    onClearRole: () -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_card_role_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_card_role_primary),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onSetPrimary() }
                        .padding(vertical = 8.dp),
                )
                primaryCards
                    .filter { it.last4 != target.last4 }
                    .forEach { primary ->
                        Text(
                            text = stringResource(
                                R.string.settings_card_role_supplementary_to,
                                formatCardLast4(primary.last4),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !updating) { onSetSupplementary(primary.last4) }
                                .padding(vertical = 8.dp),
                        )
                    }
                Text(
                    text = stringResource(R.string.settings_card_role_clear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !updating) { onClearRole() }
                        .padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsLinkDebitDialog(
    target: ManagedCardUi?,
    accounts: List<ManagedAccountUi>,
    updating: Boolean,
    onDismiss: () -> Unit,
    onLink: (ManagedAccountUi) -> Unit,
) {
    target ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_link_debit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                accounts.forEach { account ->
                    Text(
                        text = account.displayLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !updating) { onLink(account) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
fun SettingsCardMetadataActions(
    card: ManagedCardUi,
    enabled: Boolean,
    onRename: () -> Unit,
    onPickNetwork: () -> Unit,
    onPickRole: () -> Unit,
    onLinkDebit: () -> Unit,
    onMarkDebit: () -> Unit,
) {
    val isCreditCard = card.cardType != CardType.DEBIT
    val canLinkDebit = card.cardType == CardType.DEBIT ||
        card.cardNetwork == CardNetwork.MADA ||
        card.cardType == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onRename, enabled = enabled) {
            Text(stringResource(R.string.settings_action_rename))
        }
        TextButton(onClick = onPickNetwork, enabled = enabled) {
            Text(stringResource(R.string.settings_action_network))
        }
        if (isCreditCard) {
            TextButton(onClick = onPickRole, enabled = enabled) {
                Text(stringResource(R.string.settings_action_role))
            }
        }
        if (canLinkDebit) {
            TextButton(onClick = onLinkDebit, enabled = enabled) {
                Text(stringResource(R.string.settings_action_link_account))
            }
        }
        if (card.cardType != CardType.DEBIT && card.cardType != CardType.CREDIT) {
            TextButton(onClick = onMarkDebit, enabled = enabled) {
                Text(stringResource(R.string.settings_action_mark_debit))
            }
        }
    }
}

@Composable
fun cardNetworkLabel(network: CardNetwork): String =
    when (network) {
        CardNetwork.MADA -> stringResource(R.string.card_network_mada)
        CardNetwork.VISA -> stringResource(R.string.card_network_visa)
        CardNetwork.MASTERCARD -> stringResource(R.string.card_network_mastercard)
        CardNetwork.AMEX -> stringResource(R.string.card_network_amex)
        CardNetwork.UNKNOWN -> stringResource(R.string.card_network_unknown)
    }

@Composable
fun cardRoleLabel(role: CardRole?): String =
    when (role) {
        CardRole.PRIMARY -> stringResource(R.string.settings_card_role_primary)
        CardRole.SUPPLEMENTARY -> stringResource(R.string.settings_card_role_additional)
        CardRole.STANDALONE, null -> stringResource(R.string.settings_card_role_standalone)
    }
