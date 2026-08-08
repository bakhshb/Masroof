package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierAddOutcome
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.IdentifierForm
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Compact identifier-management section rendered inside the active
 * AccountEditDialog. Strict normalization rules are enforced via
 * [AccountIdentifierRepository]; we only surface Arabic feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifiersSection(
    accountId: Long,
    accountType: com.baraa.masroof.transaction.AccountType,
    onPossibleConflict: (IdentifierAddOutcome) -> Unit = {},
    onImportAfterBind: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo = app.accountIdentifierRepository
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<AccountIdentifierEntity>>(emptyList()) }
    val pendingSenderInitial = remember {
        com.baraa.masroof.ui.senders.ImportSessionHints.consumePreferredSender()
    }
    var pendingSender by remember { mutableStateOf(pendingSenderInitial) }
    var showAdd by remember { mutableStateOf(pendingSenderInitial != null) }
    var showSmsBinding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AccountIdentifierEntity?>(null) }
    LaunchedEffect(accountId) {
        repo.observeByAccount(accountId).collectLatest { items = it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("معرفات الحساب", style = MaterialTheme.typography.titleSmall)
        Text(
            "أدخل آخر 4 أرقام للحساب / البطاقة / الآيبان يدوياً. مرسل الرسائل يُختار من القسم أعلاه.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("إدخال آخر 4 أرقام يدوياً")
        }
        if (items.isEmpty()) Text("لا توجد معرفات بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { identifier ->
            IdentifierRow(
                identifier = identifier,
                onToggle = { active -> scope.launch { repo.setActive(identifier.id, active) } },
                onEdit = { editing = identifier },
                onDelete = { scope.launch { repo.delete(identifier) } },
            )
        }
        var showSmsExtract by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showSmsExtract = !showSmsExtract },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showSmsExtract) "إخفاء الاستخراج من رسالة" else "خيار متقدم: استخراج من رسالة")
        }
        if (showSmsExtract) {
            Text(
                "اختياري — يمكنك اختيار رسالة بنك لاستخراج آخر 4 أرقام تلقائياً.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showSmsBinding = true }, modifier = Modifier.fillMaxWidth()) {
                Text("استخراج آخر 4 أرقام من رسالة")
            }
        }
        if (showSmsBinding) {
            AccountSmsBindingDialog(
                accountId = accountId,
                accountType = accountType,
                onDismiss = { showSmsBinding = false },
                onImportNow = {
                    showSmsBinding = false
                    onImportAfterBind?.invoke()
                },
            )
        }
        if (showAdd) {
            AddIdentifierDialog(
                accountId = accountId,
                repository = repo,
                initialType = AccountIdentifierType.ACCOUNT_LAST4,
                initialValue = "",
                onDismiss = { showAdd = false; pendingSender = null },
                onSaved = { outcome ->
                    showAdd = false
                    pendingSender = null
                    onPossibleConflict(outcome)
                    scope.launch {
                        runCatching { app.historicalAccountRelinkService.relinkUnposted(dryRun = false) }
                    }
                },
            )
        }
        editing?.let { target ->
            EditIdentifierDialog(
                identifier = target,
                repository = repo,
                onDismiss = { editing = null },
                onSaved = {
                    editing = null
                    onPossibleConflict(it)
                },
            )
        }
    }
}

@Composable
private fun IdentifierRow(
    identifier: AccountIdentifierEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(typeLabel(identifier.identifierType), style = MaterialTheme.typography.bodyMedium)
                Text(maskedValue(identifier), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (identifier.isActive) "نشط" else "معطل",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (identifier.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onEdit) { Text("تعديل") }
            TextButton(onClick = { onToggle(!identifier.isActive) }) { Text(if (identifier.isActive) "تعطيل" else "تفعيل") }
            TextButton(onClick = onDelete) { Text("حذف") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIdentifierDialog(
    accountId: Long,
    repository: AccountIdentifierRepository,
    onDismiss: () -> Unit,
    onSaved: (IdentifierAddOutcome) -> Unit,
    initialType: AccountIdentifierType = AccountIdentifierType.ACCOUNT_LAST4,
    initialValue: String = "",
) {
    var type by remember { mutableStateOf(initialType) }
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة معرف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = typeLabel(type), onValueChange = {}, readOnly = true, label = { Text("نوع المعرف") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AccountIdentifierType.values().forEach { valueType ->
                            DropdownMenuItem(text = { Text(typeLabel(valueType)) }, onClick = { type = valueType; expanded = false })
                        }
                    }
                }
                OutlinedTextField(value = value, onValueChange = { value = it.filter { ch -> ch != ' ' } }, label = { Text("آخر 4 أرقام") }, isError = error != null, supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val outcome = repository.addOrUpdate(accountId, IdentifierForm(type, typeLabel(type), value))
                    if (outcome.result == IdentifierAddResult.Rejected) error = outcome.message
                    else onSaved(outcome)
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun EditIdentifierDialog(
    identifier: AccountIdentifierEntity,
    repository: AccountIdentifierRepository,
    onDismiss: () -> Unit,
    onSaved: (IdentifierAddOutcome) -> Unit,
) {
    var value by remember {
        mutableStateOf(identifier.normalizedValue)
    }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المعرف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(typeLabel(identifier.identifierType))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { ch -> ch != ' ' } },
                    label = { Text("آخر 4 أرقام") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val outcome = repository.updateValue(identifier.id, value)
                        if (outcome.result == IdentifierAddResult.Rejected) error = outcome.message
                        else onSaved(outcome)
                    }
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun typeLabel(type: AccountIdentifierType): String = when (type) {
    AccountIdentifierType.ACCOUNT_LAST4 -> "آخر أربعة أرقام للحساب"
    AccountIdentifierType.DEBIT_CARD_LAST4 -> "آخر أربعة أرقام لبطاقة مدى"
    AccountIdentifierType.CREDIT_CARD_LAST4 -> "آخر أربعة أرقام للبطاقة الائتمانية"
    AccountIdentifierType.IBAN_LAST4 -> "آخر أربعة أرقام للآيبان"
    AccountIdentifierType.WALLET_LAST4 -> "آخر أربعة أرقام للمحفظة"
}

private fun maskedValue(identifier: AccountIdentifierEntity): String =
    "•••• ${identifier.normalizedValue.takeLast(4)}"

