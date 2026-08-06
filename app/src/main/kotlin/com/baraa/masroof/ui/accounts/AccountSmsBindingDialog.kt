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
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.sms.BankSmsFilter
import com.baraa.masroof.sms.SmsImportRange
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Local-only three-step picker: choose message, review extracted fields, confirm binding. */
@Composable
fun AccountSmsBindingDialog(accountId: Long, accountType: AccountType, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<SmsMessage?>(null) }
    var analysis by remember { mutableStateOf<AccountSmsAnalysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableStateOf(30) }
    var senderQuery by remember { mutableStateOf("") }
    var showAllMessages by remember { mutableStateOf(false) }
    LaunchedEffect(days) {
        loading = true
        messages = runCatching { app.smsRepository.loadInbox(SmsImportRange.lastDays(LocalDate.now(), days), 100) }.getOrDefault(emptyList())
            .sortedByDescending { BankSmsFilter.classifyMessage(it.sender, it.body).isMatch }
        loading = false
    }
    val visibleMessages = messages.filter { message ->
        message.sender.orEmpty().contains(senderQuery, ignoreCase = true) &&
            (showAllMessages || BankSmsFilter.classifyMessage(message.sender, message.body).isMatch)
    }
    when {
        selected == null -> AlertDialog(onDismissRequest = onDismiss, title = { Text("اختر رسالة تخص هذا الحساب") }, text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيتم استخدام الرسالة لاستخراج اسم المرسل ومعرف الحساب فقط، ولن يتم إرسالها خارج الجهاز.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text("آخر 7 أيام") })
                    FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text("آخر 30 يومًا") })
                }
                OutlinedTextField(senderQuery, { senderQuery = it }, label = { Text("البحث باسم المرسل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FilterChip(selected = showAllMessages, onClick = { showAllMessages = !showAllMessages }, label = { Text(if (showAllMessages) "عرض الرسائل المالية فقط" else "عرض كل الرسائل") })
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (visibleMessages.isEmpty()) Text("لم يتم العثور على رسائل مناسبة. جرّب توسيع الفترة أو اختر إدخال المعرفات يدويًا.")
                else LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleMessages, key = { it.id }) { message ->
                        val likely = BankSmsFilter.classifyMessage(message.sender, message.body).isMatch
                        OutlinedCard(onClick = { selected = message; analysis = AccountSmsAnalyzer.analyze(message, accountType) }, modifier = Modifier.fillMaxWidth()) {
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
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
        else -> AlertDialog(onDismissRequest = onDismiss, title = { Text("تم التعرف على بيانات الرسالة") }, text = {
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
            TextButton(enabled = analysis != null, onClick = {
                val value = analysis ?: return@TextButton
                scope.launch {
                    val sender = app.accountIdentifierRepository.addOrUpdate(accountId, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, value.senderDisplay, value.senderDisplay))
                    val identifier = value.identifierType?.let { type -> app.accountIdentifierRepository.addOrUpdate(accountId, IdentifierForm(type, identifierLabel(type), value.lastFour.orEmpty())) }
                    if (sender.result == IdentifierAddResult.Rejected || sender.identifier == null ||
                        (identifier != null && (identifier.result == IdentifierAddResult.Rejected || identifier.identifier == null))
                    ) error = "تعذر حفظ الربط. راجع البيانات ثم حاول مرة أخرى."
                    else onDismiss()
                }
            }) { Text("ربط بالحساب") }
        }, dismissButton = { TextButton(onClick = { selected = null; analysis = null }) { Text("اختيار رسالة أخرى") } })
    }
}

private fun identifierLabel(type: AccountIdentifierType): String = when (type) {
    AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
    AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
    AccountIdentifierType.IBAN_LAST4 -> "آيبان"
    AccountIdentifierType.WALLET_LAST4 -> "محفظة"
    AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
    AccountIdentifierType.SENDER_ALIAS -> "اسم المرسل"
}
