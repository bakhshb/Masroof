package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baraa.masroof.accounts.AccountInputValidator
import com.baraa.masroof.accounts.DuplicateAccountDetector
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.ui.theme.CalendarDateField
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Editable account values. Identifiers are managed via IdentifiersSection. */
data class AccountDraft(
    val displayName: String,
    val institutionName: String?,
    val accountType: AccountType,
    val accountNature: AccountNature,
    val currency: Currency,
    val openingBalance: BigDecimal,
    val openingBalanceDate: Long,
    val includeInNetWorth: Boolean,
    val includeInLiquidity: Boolean,
    val isActive: Boolean,
    val notes: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditDialog(
    existing: FinancialAccount?,
    existingAccounts: List<FinancialAccount> = emptyList(),
    defaultOpeningDate: LocalDate = LocalDate.now(),
    onDismiss: () -> Unit,
    onSave: (AccountDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    onImportAfterBind: (() -> Unit)? = null,
) {
    var displayName by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var institutionName by remember(existing) { mutableStateOf(existing?.institutionName.orEmpty()) }
    val knownBanks = remember {
        com.baraa.masroof.ledger.FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS
    }
    var institutionCustom by remember(existing) {
        mutableStateOf(
            !existing?.institutionName.isNullOrBlank() &&
                existing?.institutionName !in knownBanks,
        )
    }
    var institutionExpanded by remember { mutableStateOf(false) }
    var accountType by remember(existing) { mutableStateOf(existing?.accountType ?: AccountType.BANK_ACCOUNT) }
    var accountNature by remember(existing) {
        mutableStateOf(existing?.accountNature ?: AccountNature.defaultNatureFor(accountType))
    }
    var amountText by remember(existing) { mutableStateOf(existing?.openingBalance?.toPlainString().orEmpty()) }
    var openingDate by remember(existing, defaultOpeningDate) {
        mutableStateOf(existing?.openingBalanceDate?.takeIf { it > 0L }?.toLocalDate() ?: defaultOpeningDate)
    }
    var currency by remember(existing) { mutableStateOf(existing?.currency ?: Currency.SAR) }
    var includeInNetWorth by remember(existing) { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var includeInLiquidity by remember(existing) {
        mutableStateOf(existing?.includeInLiquidity ?: AccountLiquidityDefaults.defaultFor(accountType))
    }
    var isActive by remember(existing) { mutableStateOf(existing?.isActive ?: true) }
    var notes by remember(existing) { mutableStateOf(existing?.notes.orEmpty()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val balance = amountText.toBigDecimalOrNull()
    val date = openingDate
    val errors = if (balance != null) {
        AccountInputValidator.validate(displayName, balance, date)
    } else {
        emptyList()
    }
    val valid = balance != null && errors.isEmpty()
    val duplicateWarning = DuplicateAccountDetector.isDuplicate(
        candidate = DuplicateAccountDetector.AccountToCheck(
            institutionName = institutionName,
            accountType = accountType,
            accountNature = accountNature,
        ),
        existing = existingAccounts.filterNot { it.id == existing?.id },
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة حساب" else "تعديل الحساب") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("اسم الحساب") },
                    isError = displayName.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = institutionExpanded,
                    onExpandedChange = { institutionExpanded = !institutionExpanded },
                ) {
                    OutlinedTextField(
                        value = when {
                            institutionCustom -> "أخرى"
                            institutionName.isBlank() -> ""
                            else -> institutionName
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("البنك / المؤسسة") },
                        placeholder = { Text("اختر البنك") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(institutionExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(institutionExpanded, { institutionExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("— بدون تحديد —") },
                            onClick = {
                                institutionName = ""
                                institutionCustom = false
                                institutionExpanded = false
                            },
                        )
                        knownBanks.forEach { bank ->
                            DropdownMenuItem(
                                text = { Text(bank) },
                                onClick = {
                                    institutionName = bank
                                    institutionCustom = false
                                    institutionExpanded = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("أخرى…") },
                            onClick = {
                                if (!institutionCustom) institutionName = ""
                                institutionCustom = true
                                institutionExpanded = false
                            },
                        )
                    }
                }
                if (institutionCustom) {
                    OutlinedTextField(
                        value = institutionName,
                        onValueChange = { institutionName = it },
                        label = { Text("اسم المؤسسة (أخرى)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                ) {
                    OutlinedTextField(
                        value = accountTypeLabel(accountType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع الحساب") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                        AccountType.setupTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(accountTypeLabel(type)) },
                                onClick = {
                                    accountType = type
                                    accountNature = AccountNature.defaultNatureFor(type)
                                    includeInLiquidity = AccountLiquidityDefaults.defaultFor(type)
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Text("طبيعة الحساب: ${accountNatureLabel(accountNature)}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            if (accountType == AccountType.CREDIT_CARD) "المبلغ المستحق الافتتاحي"
                            else "الرصيد الافتتاحي",
                        )
                    },
                    supportingText = {
                        Text(
                            if (accountType == AccountType.CREDIT_CARD) {
                                "أدخل ما تدين به البطاقة اليوم كمبلغ موجب (صفر إن لم يكن هناك مستحق)."
                            } else {
                                "أدخل الالتزامات كمبالغ موجبة"
                            },
                        )
                    },
                    isError = balance == null || errors.any { it.key == AccountInputValidator.ErrorKey.NEGATIVE_OPENING_BALANCE },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CalendarDateField(
                    label = "تاريخ الرصيد الافتتاحي",
                    selected = openingDate,
                    onSelected = { openingDate = it },
                    maxDate = LocalDate.now(),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = currencyExpanded,
                    onExpandedChange = { currencyExpanded = !currencyExpanded },
                ) {
                    OutlinedTextField(
                        value = currency.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العملة") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(currencyExpanded, { currencyExpanded = false }) {
                        Currency.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = { currency = option; currencyExpanded = false },
                            )
                        }
                    }
                }
                if (accountType == AccountType.CREDIT_CARD) {
                    ToggleRow("يُطرح من صافي الثروة (التزام)", includeInNetWorth) { includeInNetWorth = it }
                    ToggleRow("يدخل في السيولة المتاحة", includeInLiquidity) { includeInLiquidity = it }
                    if (includeInLiquidity) {
                        Text(
                            "بطاقة الائتمان دين وليست نقدًا متاحًا. تفعيل السيولة غير موصى به.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    ToggleRow("يدخل في صافي الثروة", includeInNetWorth) { includeInNetWorth = it }
                    ToggleRow("يدخل في السيولة", includeInLiquidity) { includeInLiquidity = it }
                }
                if (existing != null) {
                    ToggleRow("الحساب نشط", isActive) { isActive = it }
                }
                if (duplicateWarning) {
                    Text(
                        "يوجد حساب مشابه. يمكنك الحفظ إذا كان مقصودًا.",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
                if (existing == null) {
                    Text(
                        "بعد الحفظ اختر مرسل الرسائل وأضف معرفات الحساب (آخر 4 أرقام) يدوياً.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                existing?.let { account ->
                    AccountSenderProfilesSection(accountId = account.id)
                    IdentifiersSection(
                        accountId = account.id,
                        accountType = account.accountType,
                        onImportAfterBind = onImportAfterBind,
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) { Text("إيقاف الحساب") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        AccountDraft(
                            displayName = displayName.trim(),
                            institutionName = institutionName.trim().takeIf(String::isNotEmpty),
                            accountType = accountType,
                            accountNature = accountNature,
                            currency = currency,
                            openingBalance = balance!!,
                            openingBalanceDate = date.toEpochMillis(),
                            includeInNetWorth = includeInNetWorth,
                            includeInLiquidity = includeInLiquidity && accountNature == AccountNature.ASSET,
                            isActive = isActive,
                            notes = notes.trim().takeIf(String::isNotEmpty),
                        ),
                    )
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("إيقاف الحساب؟") },
            text = { Text("سيُحفظ الحساب وتاريخه وقيوده، لكنه لن يُستخدم في المطابقة أو الاستيراد الجديد.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("إيقاف") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun accountNatureLabel(nature: AccountNature): String = when (nature) {
    AccountNature.ASSET -> "أصل"
    AccountNature.LIABILITY -> "التزام"
}

internal fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.BANK_ACCOUNT -> "حساب بنكي"
    AccountType.CREDIT_CARD -> "بطاقة ائتمانية"
    AccountType.DIGITAL_WALLET, AccountType.WALLET -> "محفظة رقمية"
    AccountType.CASH -> "نقد"
    AccountType.INVESTMENT_ACCOUNT -> "استثمار"
    AccountType.SUKUK_ACCOUNT -> "صكوك"
    AccountType.LOAN -> "قرض"
    AccountType.OTHER_ASSET -> "أصل آخر"
    AccountType.OTHER_LIABILITY -> "التزام آخر"
    AccountType.OTHER -> "أخرى"
}
