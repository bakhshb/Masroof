package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.rules.ReviewStateMachine
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Edit / review dialog for a single [TransactionEntity].
 *
 * Editable fields: type, amount, currency, merchant, date, status. The
 * "review actions" row exposes quick actions for common user decisions:
 * "تأكيد", "اعتبارها تحويلًا داخليًا", "اعتبارها استثمارًا", "تجاهل العملية".
 *
 * A "تذكر هذا التصنيف لهذا التاجر مستقبلًا" checkbox appears whenever a
 * callback for persisting the merchant memory is provided. When checked,
 * saving the transaction also calls [onConfirmAndRememberMerchant] which
 * is responsible for writing to [com.baraa.masroof.data.repository.MerchantMemoryRepository].
 *
 * All field transitions go through [ReviewStateMachine] so the
 * `userConfirmed` / `needsReview` / `categorySource` / `categoryId` /
 * `financialTreatment` flags cannot drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    entity: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmAndRememberMerchant: ((TransactionEntity) -> Unit)? = null,
    rememberAvailable: Boolean = true,
) {
    var type by remember { mutableStateOf(entity.transactionType) }
    var amountText by remember { mutableStateOf(entity.amount?.toPlainString().orEmpty()) }
    var currency by remember { mutableStateOf(entity.currency) }
    var merchant by remember { mutableStateOf(entity.merchantOrBeneficiary.orEmpty()) }
    var dateText by remember { mutableStateOf(entity.transactionDate?.toString().orEmpty()) }
    var status by remember { mutableStateOf(entity.status) }
    var rememberMerchant by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val amountInvalidMsg = stringResource(id = R.string.edit_dialog_invalid_amount)
    val dateInvalidMsg = stringResource(id = R.string.edit_dialog_invalid_date)
    var amountError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.delete_one_title)) },
            text = { Text(stringResource(id = R.string.delete_one_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onConfirmDelete()
                }) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.edit_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EnumDropdown(
                    label = stringResource(id = R.string.tx_type),
                    selected = type,
                    values = TransactionType.values().toList(),
                    labelFor = { typeLabel(it) },
                    onSelect = { type = it },
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text(stringResource(id = R.string.edit_dialog_amount_label)) },
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = stringResource(id = R.string.tx_currency),
                    selected = currency,
                    values = Currency.values().toList(),
                    labelFor = { currencyLabel(it) },
                    onSelect = { currency = it },
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(id = R.string.edit_dialog_merchant_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        dateError = null
                    },
                    label = { Text(stringResource(id = R.string.edit_dialog_date_label)) },
                    isError = dateError != null,
                    supportingText = dateError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumDropdown(
                    label = stringResource(id = R.string.tx_status),
                    selected = status,
                    values = TransactionStatus.values().toList(),
                    labelFor = { statusLabel(it) },
                    onSelect = { status = it },
                )
                if (rememberAvailable && onConfirmAndRememberMerchant != null && merchant.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = rememberMerchant,
                            onCheckedChange = { rememberMerchant = it },
                        )
                        Text(
                            text = stringResource(id = R.string.review_apply_and_remember),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = {
                        onSave(
                            ReviewStateMachine.confirm(
                                entity = entity.copy(
                                    transactionType = type,
                                    amount = parseAmount(amountText),
                                    currency = currency,
                                    merchantOrBeneficiary = merchant.ifBlank { null },
                                    transactionDate = parseDate(dateText),
                                    status = status,
                                ),
                            )
                        )
                    },
                ) { Text(stringResource(id = R.string.review_action_confirm)) }
                TextButton(
                    onClick = {
                        onSave(
                            ReviewStateMachine.forceTreatment(
                                entity = entity.copy(
                                    transactionType = type,
                                    amount = parseAmount(amountText),
                                    currency = currency,
                                    merchantOrBeneficiary = merchant.ifBlank { null },
                                    transactionDate = parseDate(dateText),
                                    status = status,
                                ),
                                treatment = FinancialTreatment.INTERNAL_TRANSFER,
                            )
                        )
                    },
                ) { Text(stringResource(id = R.string.review_action_mark_internal)) }
                TextButton(
                    onClick = {
                        onSave(
                            ReviewStateMachine.forceTreatment(
                                entity = entity.copy(
                                    transactionType = type,
                                    amount = parseAmount(amountText),
                                    currency = currency,
                                    merchantOrBeneficiary = merchant.ifBlank { null },
                                    transactionDate = parseDate(dateText),
                                    status = status,
                                ),
                                treatment = FinancialTreatment.INVESTMENT,
                            )
                        )
                    },
                ) { Text(stringResource(id = R.string.review_action_mark_investment)) }
                TextButton(
                    onClick = {
                        onSave(
                            ReviewStateMachine.forceTreatment(
                                entity = entity.copy(
                                    transactionType = type,
                                    amount = parseAmount(amountText),
                                    currency = currency,
                                    merchantOrBeneficiary = merchant.ifBlank { null },
                                    transactionDate = parseDate(dateText),
                                    status = status,
                                ),
                                treatment = FinancialTreatment.IGNORED,
                            )
                        )
                    },
                ) { Text(stringResource(id = R.string.review_action_ignore)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedAmount = parseAmount(amountText)
                    if (amountText.isNotBlank() && parsedAmount == null) {
                        amountError = amountInvalidMsg
                        return@TextButton
                    }
                    val parsedDate = parseDate(dateText)
                    if (dateText.isNotBlank() && parsedDate == null) {
                        dateError = dateInvalidMsg
                        return@TextButton
                    }
                    val updated = ReviewStateMachine.confirm(
                        entity = entity,
                        categoryId = entity.categoryId,
                        categorySource = CategorySource.USER,
                    ).copy(
                        transactionType = type,
                        amount = parsedAmount,
                        currency = currency,
                        merchantOrBeneficiary = merchant.ifBlank { null },
                        transactionDate = parsedDate,
                        status = status,
                    )
                    if (rememberMerchant && onConfirmAndRememberMerchant != null) {
                        onConfirmAndRememberMerchant(updated)
                    } else {
                        onSave(updated)
                    }
                },
            ) { Text(stringResource(id = R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <E : Enum<E>> EnumDropdown(
    label: String,
    selected: E,
    values: List<E>,
    labelFor: @Composable (E) -> String,
    onSelect: (E) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = labelFor(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (v in values) {
                DropdownMenuItem(
                    text = { Text(labelFor(v)) },
                    onClick = {
                        onSelect(v)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun parseAmount(text: String): BigDecimal? =
    if (text.isBlank()) null else runCatching { BigDecimal(text.trim()) }.getOrNull()

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun parseDate(text: String): LocalDate? =
    if (text.isBlank()) null else runCatching {
        LocalDate.parse(text.trim(), DATE_FMT)
    }.recoverCatching {
        DateTimeFormatter.ofPattern("yyyy/MM/dd").runCatching { LocalDate.parse(text.trim(), this) }.getOrNull()
            ?: throw DateTimeParseException("unparseable", text, 0)
    }.getOrNull()

@Composable
private fun typeLabel(t: TransactionType): String = stringResource(
    id = when (t) {
        TransactionType.PURCHASE -> R.string.type_purchase
        TransactionType.ONLINE_PURCHASE -> R.string.type_online_purchase
        TransactionType.CASH_WITHDRAWAL -> R.string.type_cash_withdrawal
        TransactionType.TRANSFER_OUT -> R.string.type_transfer_out
        TransactionType.TRANSFER_IN -> R.string.type_transfer_in
        TransactionType.CARD_PAYMENT -> R.string.type_card_payment
        TransactionType.REFUND -> R.string.type_refund
        TransactionType.SALARY -> R.string.type_salary
        TransactionType.DEPOSIT -> R.string.type_deposit
        TransactionType.BANK_FEE -> R.string.type_bank_fee
        TransactionType.INTERNAL_TRANSFER -> R.string.type_internal_transfer
        TransactionType.INVESTMENT_TRANSFER -> R.string.type_investment_transfer
        TransactionType.DECLINED -> R.string.type_declined
        TransactionType.UNKNOWN -> R.string.type_unknown
    }
)

@Composable
private fun statusLabel(s: TransactionStatus): String = stringResource(
    id = when (s) {
        TransactionStatus.COMPLETED -> R.string.status_completed
        TransactionStatus.PENDING -> R.string.status_pending
        TransactionStatus.DECLINED -> R.string.status_declined
        TransactionStatus.REVERSED -> R.string.status_reversed
        TransactionStatus.UNKNOWN -> R.string.status_unknown
        TransactionStatus.NEEDS_REVIEW -> R.string.status_needs_review
    }
)

@Composable
private fun currencyLabel(c: Currency): String = stringResource(
    id = when (c) {
        Currency.SAR -> R.string.currency_sar
        Currency.USD -> R.string.currency_usd
        Currency.EUR -> R.string.currency_eur
        Currency.UNKNOWN -> R.string.currency_unknown
    }
)
