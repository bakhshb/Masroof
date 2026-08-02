package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountType

/**
 * Add / edit dialog for a single [FinancialAccount]. Only the last 4 digits
 * are ever stored — there is no field for a full account / card number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditDialog(
    existing: FinancialAccount?,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        institutionName: String?,
        accountType: AccountType,
        lastFourDigits: String?,
        senderAliases: List<String>,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var displayName by remember { mutableStateOf(existing?.displayName.orEmpty()) }
    var institutionName by remember { mutableStateOf(existing?.institutionName.orEmpty()) }
    var accountType by remember { mutableStateOf(existing?.accountType ?: AccountType.BANK_ACCOUNT) }
    var lastFour by remember { mutableStateOf(existing?.lastFourDigits.orEmpty()) }
    var aliasesText by remember { mutableStateOf(existing?.senderAliases?.joinToString(", ").orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.account_add_title else R.string.account_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(id = R.string.account_field_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = institutionName,
                    onValueChange = { institutionName = it },
                    label = { Text(stringResource(id = R.string.account_field_institution)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Account type dropdown
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                ) {
                    OutlinedTextField(
                        value = accountTypeLabel(accountType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.account_field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        AccountType.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(accountTypeLabel(t)) },
                                onClick = {
                                    accountType = t
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = lastFour,
                    onValueChange = { input -> lastFour = input.take(4).filter { it.isDigit() } },
                    label = { Text(stringResource(id = R.string.account_field_last_four)) },
                    supportingText = { Text(stringResource(id = R.string.account_field_last_four_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = aliasesText,
                    onValueChange = { aliasesText = it },
                    label = { Text(stringResource(id = R.string.account_field_aliases)) },
                    supportingText = { Text(stringResource(id = R.string.account_field_aliases_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            text = stringResource(id = R.string.action_delete),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        displayName.trim(),
                        institutionName.takeIf { it.isNotBlank() }?.trim(),
                        accountType,
                        lastFour.takeIf { it.length == 4 },
                        aliasesText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    )
                },
                enabled = displayName.isNotBlank(),
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.account_delete_title)) },
            text = { Text(stringResource(id = R.string.account_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            },
        )
    }
}

private fun accountTypeLabel(t: AccountType): String = when (t) {
    AccountType.BANK_ACCOUNT -> "حساب بنكي"
    AccountType.CREDIT_CARD -> "بطاقة ائتمان"
    AccountType.WALLET -> "محفظة"
    AccountType.CASH -> "نقد"
    AccountType.INVESTMENT_ACCOUNT -> "حساب استثماري"
    AccountType.OTHER -> "أخرى"
}
