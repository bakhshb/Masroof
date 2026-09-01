package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.application.settings.SettingsCommitmentsWorkflow
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.MasroofSectionHeader
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.presentation.theme.MasroofSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCommitmentEditorScreen(
    commitment: ManagedCommitmentUi,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (SettingsCommitmentsWorkflow.CommitmentEditorDraft) -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(commitment.name) }
    var amountText by rememberSaveable { mutableStateOf(commitment.amount.amount.toPlainString()) }
    var transactionDate by rememberSaveable { mutableStateOf(commitment.transactionDate.toString()) }
    var recurrence by rememberSaveable { mutableStateOf(commitment.recurrence?.name) }
    var dueDate by rememberSaveable(commitment.dueDate?.toString()) {
        mutableStateOf(commitment.dueDate?.toString())
    }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showTransactionDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDueDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_commitment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_commitment_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.settings_commitment_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (showTransactionDatePicker) {
        CommitmentDatePickerDialog(
            initialDate = LocalDate.parse(transactionDate),
            onDismiss = { showTransactionDatePicker = false },
            onConfirm = {
                transactionDate = it.toString()
                showTransactionDatePicker = false
            },
        )
    }

    if (showDueDatePicker) {
        CommitmentDatePickerDialog(
            initialDate = dueDate?.let(LocalDate::parse) ?: LocalDate.now(),
            onDismiss = { showDueDatePicker = false },
            onConfirm = {
                dueDate = it.toString()
                showDueDatePicker = false
            },
        )
    }

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_commitment_edit_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = MasroofSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionGap),
        ) {
            MasroofCard {
                Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.settings_commitment_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(stringResource(R.string.settings_commitment_amount)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = { showTransactionDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_commitment_transaction_date,
                            ) + ": " + formatLocalizedTransactionDate(LocalDate.parse(transactionDate)),
                        )
                    }
                    RecurrenceSelector(
                        selected = recurrence?.let { runCatching { CommitmentRecurrence.valueOf(it) }.getOrNull() },
                        onSelect = { selected -> recurrence = selected?.name },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionHeaderGap),
                    ) {
                        OutlinedButton(
                            onClick = { showDueDatePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                dueDate?.let {
                                    formatLocalizedTransactionDate(LocalDate.parse(it))
                                } ?: stringResource(R.string.settings_commitment_due_date_none),
                            )
                        }
                        if (dueDate != null) {
                            OutlinedButton(onClick = { dueDate = null }) {
                                Text(stringResource(R.string.settings_commitment_due_date_clear))
                            }
                        }
                    }
                }
            }

            IconTextButtonOutlined(
                onClick = {
                    val parsedAmount = amountText.trim().toBigDecimalOrNull() ?: return@IconTextButtonOutlined
                    onSave(
                        SettingsCommitmentsWorkflow.CommitmentEditorDraft(
                            name = name.trim(),
                            amount = Money(parsedAmount.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN), commitment.amount.currency),
                            transactionDate = LocalDate.parse(transactionDate),
                            recurrence = recurrence?.let { CommitmentRecurrence.valueOf(it) },
                            dueDate = dueDate?.let(LocalDate::parse),
                        ),
                    )
                },
                enabled = !saving && name.isNotBlank() && amountText.isNotBlank(),
                icon = MasroofIcons.reviewQueue,
                text = stringResource(R.string.settings_commitment_save),
                modifier = Modifier.fillMaxWidth(),
            )

            IconTextButtonOutlined(
                onClick = onToggleActive,
                enabled = !saving,
                icon = if (commitment.active) MasroofIcons.warning else MasroofIcons.externalIn,
                text = stringResource(
                    if (commitment.active) {
                        R.string.settings_commitment_disable
                    } else {
                        R.string.settings_commitment_enable
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_commitment_delete))
            }
        }
    }
}

@Composable
private fun RecurrenceSelector(
    selected: CommitmentRecurrence?,
    onSelect: (CommitmentRecurrence?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
        MasroofSectionHeader(
            title = stringResource(R.string.settings_commitment_recurrence),
            icon = MasroofIcons.periodHint,
        )
        recurrenceOptions().forEach { (value, labelRes) ->
            OutlinedButton(
                onClick = { onSelect(value) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(labelRes),
                    color = if (selected == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun recurrenceOptions(): List<Pair<CommitmentRecurrence?, Int>> =
    listOf(
        null to R.string.settings_commitment_recurrence_none,
        CommitmentRecurrence.WEEKLY to R.string.settings_commitment_recurrence_weekly,
        CommitmentRecurrence.MONTHLY to R.string.settings_commitment_recurrence_monthly,
        CommitmentRecurrence.YEARLY to R.string.settings_commitment_recurrence_yearly,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitmentDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
                }
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
