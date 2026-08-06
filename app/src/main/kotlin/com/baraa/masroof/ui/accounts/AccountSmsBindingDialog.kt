package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.accounts.AccountSmsAnalysis
import com.baraa.masroof.accounts.AccountSmsAnalyzer
import com.baraa.masroof.accounts.SmsBindingStateHolder
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Compose adapter around [SmsBindingStateHolder]; no direct repository calls. */
@Composable
fun AccountSmsBindingDialog(accountId: Long, accountType: com.baraa.masroof.transaction.AccountType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val holder = remember(app, accountId) { SmsBindingStateHolder(app.smsRepository, app.accountIdentifierRepository) }
    val scope = rememberCoroutineScope()
    val state by holder.state.collectAsState()
    LaunchedEffect(accountId) { holder.refresh() }
    LaunchedEffect(state.committed) { if (state.committed) onDismiss() }

    when (state.selected) {
        null -> PickerDialog(
            state = state,
            onDays = { scope.launch { holder.refreshFor(it) } },
            onQuery = holder::setSenderQuery,
            onShowAll = holder::setShowAll,
            onSelect = { holder.choose(it, accountType) },
        )
        else -> ConfirmDialog(
            analysis = state.analysis,
            error = state.error,
            onAnother = holder::chooseAnother,
            onConfirm = { scope.launch { holder.commit(accountId) } },
        )
    }
}

@Composable
private fun PickerDialog(
    state: SmsBindingStateHolder.State,
    onDays: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onShowAll: (Boolean) -> Unit,
    onSelect: (com.baraa.masroof.sms.SmsMessage) -> Unit,
) {
    AlertDialog(onDismissRequest = {}, title = { Text("اختر رسالة تخص هذا الحساب") }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("سيتم استخدام الرسالة لاستخراج اسم المرسل ومعرف الحساب فقط، ولن يتم إرسالها خارج الجهاز.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.days == 7, onClick = { onDays(7) }, label = { Text("آخر 7 أيام") })
                FilterChip(selected = state.days == 30, onClick = { onDays(30) }, label = { Text("آخر 30 يومًا") })
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
    }, confirmButton = {})
}

@Composable
private fun ConfirmDialog(analysis: AccountSmsAnalysis?, error: String?, onAnother: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = {}, title = { Text("تم التعرف على بيانات الرسالة") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val value = analysis
            if (value == null) Text("تعذر استخراج بيانات آمنة من الرسالة.") else {
                Text("مرسل الرسائل: ${value.senderDisplay}")
                Text("نوع الرسالة: ${value.transactionTypeLabel}")
                value.identifierType?.let { Text("المعرف: ${identifierLabel(it)} ••••${value.lastFour}") }
                Text("مستوى الثقة: ${value.confidence}%")
                value.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = analysis != null, onClick = onConfirm) { Text("ربط بالحساب") }
    }, dismissButton = { TextButton(onClick = onAnother) { Text("اختيار رسالة أخرى") } })
}

private fun identifierLabel(type: com.baraa.masroof.data.db.AccountIdentifierType): String = when (type) {
    com.baraa.masroof.data.db.AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
    com.baraa.masroof.data.db.AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
    com.baraa.masroof.data.db.AccountIdentifierType.IBAN_LAST4 -> "آيبان"
    com.baraa.masroof.data.db.AccountIdentifierType.WALLET_LAST4 -> "محفظة"
    com.baraa.masroof.data.db.AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
    com.baraa.masroof.data.db.AccountIdentifierType.SENDER_ALIAS -> "اسم المرسل"
}