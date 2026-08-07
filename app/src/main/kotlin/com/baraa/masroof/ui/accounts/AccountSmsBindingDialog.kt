package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.accounts.AccountSmsAnalysis
import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.accounts.SmsBindingStateHolder
import com.baraa.masroof.ui.senders.ImportSessionHints
import com.baraa.masroof.ui.theme.CalendarDateField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Compose adapter around [SmsBindingStateHolder]; no direct repository calls. */
@Composable
fun AccountSmsBindingDialog(
    accountId: Long,
    accountType: com.baraa.masroof.transaction.AccountType,
    onDismiss: () -> Unit,
    onImportNow: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val holder = remember(app, accountId) {
        SmsBindingStateHolder(
            smsRepository = app.smsRepository,
            identifierRepository = app.accountIdentifierRepository,
            afterBindRelink = { app.historicalAccountRelinkService.relinkUnposted(dryRun = false) },
        )
    }
    val scope = rememberCoroutineScope()
    val state by holder.state.collectAsState()
    var showSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(accountId) { holder.refresh() }
    LaunchedEffect(state.committed) {
        if (state.committed) {
            val account = app.financialAccountRepository.getById(accountId)
            ImportSessionHints.setPreferredFromEpochMillis(account?.openingBalanceDate)
            showSuccess = true
        }
    }

    when {
        showSuccess -> BindSuccessDialog(
            onImportNow = {
                showSuccess = false
                onImportNow?.invoke() ?: onDismiss()
            },
            onLater = {
                showSuccess = false
                onDismiss()
            },
        )
        state.selected == null -> PickerDialog(
            state = state,
            onRangeMode = { mode -> scope.launch { holder.refreshFor(mode) } },
            onCustomFrom = { date ->
                holder.setCustomFrom(date)
                scope.launch { holder.refreshFor(SmsBindingStateHolder.RangeMode.CUSTOM_FROM, date) }
            },
            onQuery = holder::setSenderQuery,
            onShowAll = holder::setShowAll,
            onSelect = { holder.choose(it, accountType) },
            onCancel = onDismiss,
        )
        else -> ConfirmDialog(
            analysis = state.analysis,
            error = state.error,
            onAnother = holder::chooseAnother,
            onConfirm = { scope.launch { holder.commit(accountId) } },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun BindSuccessDialog(onImportNow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("تم ربط الحساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تم حفظ مرسل الرسائل ومعرف الحساب.")
                Text("الربط وحده لا يستورد العمليات. استورد رسائل البنك من تاريخ الرصيد الافتتاحي حتى يظهر الرصيد والمخططات.")
            }
        },
        confirmButton = {
            TextButton(onClick = onImportNow) { Text("استيراد الرسائل الآن") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("لاحقاً") }
        },
    )
}

@Composable
private fun PickerDialog(
    state: SmsBindingStateHolder.State,
    onRangeMode: (SmsBindingStateHolder.RangeMode) -> Unit,
    onCustomFrom: (LocalDate) -> Unit,
    onQuery: (String) -> Unit,
    onShowAll: (Boolean) -> Unit,
    onSelect: (com.baraa.masroof.sms.SmsMessage) -> Unit,
    onCancel: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("اختر رسالة تخص هذا الحساب") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيتم استخدام الرسالة لاستخراج اسم المرسل ومعرف الحساب فقط، ولن يتم إرسالها خارج الجهاز.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.rangeMode == SmsBindingStateHolder.RangeMode.LAST_7,
                        onClick = { onRangeMode(SmsBindingStateHolder.RangeMode.LAST_7) },
                        label = { Text("آخر 7 أيام") },
                    )
                    FilterChip(
                        selected = state.rangeMode == SmsBindingStateHolder.RangeMode.LAST_30,
                        onClick = { onRangeMode(SmsBindingStateHolder.RangeMode.LAST_30) },
                        label = { Text("آخر 30 يومًا") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.rangeMode == SmsBindingStateHolder.RangeMode.LAST_SALARY,
                        onClick = { onRangeMode(SmsBindingStateHolder.RangeMode.LAST_SALARY) },
                        label = { Text("منذ آخر راتب") },
                    )
                    FilterChip(
                        selected = state.rangeMode == SmsBindingStateHolder.RangeMode.CUSTOM_FROM,
                        onClick = { onRangeMode(SmsBindingStateHolder.RangeMode.CUSTOM_FROM) },
                        label = { Text("تحديد تاريخ") },
                    )
                }
                if (state.rangeLabel.isNotBlank()) {
                    Text("الفترة: ${state.rangeLabel}", style = MaterialTheme.typography.labelMedium)
                }
                if (state.rangeMode == SmsBindingStateHolder.RangeMode.CUSTOM_FROM) {
                    CalendarDateField(
                        label = "من تاريخ",
                        selected = state.customFrom,
                        onSelected = onCustomFrom,
                        maxDate = today,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(state.senderQuery, { onQuery(it) }, label = { Text("البحث باسم المرسل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FilterChip(selected = state.showAllMessages, onClick = { onShowAll(!state.showAllMessages) }, label = { Text(if (state.showAllMessages) "عرض الرسائل المالية فقط" else "عرض كل الرسائل") })
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (state.visibleMessages.isEmpty()) Text("لم يتم العثور على رسائل مناسبة. جرّب توسيع الفترة أو اختر إدخال المعرفات يدويًا.")
                else LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.visibleMessages, key = { it.id }) { message ->
                        val likely = com.baraa.masroof.sms.BankSmsFilter.classifyMessage(message.sender, message.body).isMatch
                        OutlinedCard(onClick = { onSelect(message) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(message.sender ?: "مرسل غير معروف", style = MaterialTheme.typography.titleSmall)
                                Text(Instant.ofEpochMilli(message.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.labelSmall)
                                Text(if (likely) "رسالة مالية محتملة" else "رسالة أخرى", style = MaterialTheme.typography.labelSmall)
                                AccountSmsAnalyzer.sanitizedPreview(message.body).takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } },
    )
}

@Composable
private fun ConfirmDialog(
    analysis: AccountSmsAnalysis?,
    error: String?,
    onAnother: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("تم التعرف على بيانات الرسالة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val value = analysis
                if (value == null) Text("تعذر استخراج بيانات آمنة من الرسالة.") else {
                    Text("مرسل الرسائل: ${value.senderDisplay}")
                    Text("نوع الرسالة: ${value.transactionTypeLabel}")
                    value.identifierType?.let { type ->
                        Text("المعرف: ${identifierLabel(type)}")
                        value.lastFour?.let { Text("آخر 4: $it") }
                    }
                    if (value.identifierType == null && value.lastFour != null) {
                        Text("آخر 4: ${value.lastFour}")
                    }
                    Text("مستوى الثقة: ${value.confidence}%")
                    value.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = analysis != null, onClick = onConfirm) { Text("ربط بالحساب") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAnother) { Text("اختيار رسالة أخرى") }
                TextButton(onClick = onCancel) { Text("إلغاء") }
            }
        },
    )
}

private fun identifierLabel(type: com.baraa.masroof.data.db.AccountIdentifierType): String = when (type) {
    com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
    com.baraa.masroof.data.db.AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
    com.baraa.masroof.data.db.AccountIdentifierType.IBAN_LAST4 -> "آيبان"
    com.baraa.masroof.data.db.AccountIdentifierType.WALLET_LAST4 -> "محفظة"
    com.baraa.masroof.data.db.AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
    com.baraa.masroof.data.db.AccountIdentifierType.SENDER_ALIAS -> "اسم المرسل"
}
