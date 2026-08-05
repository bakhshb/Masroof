package com.baraa.masroof.ui.senders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.SenderInstitutionMappingEntity
import com.baraa.masroof.ledger.FinancialInstitutionResolver
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SenderMappingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val repo = app.senderInstitutionMappingRepository
    val scope = rememberCoroutineScope()
    var mappings by remember { mutableStateOf<List<SenderInstitutionMappingEntity>>(emptyList()) }
    var newSender by remember { mutableStateOf("") }
    var newInstitution by remember { mutableStateOf("") }
    var editState by remember { mutableStateOf<EditState?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { repo.observeAll().collectLatest { mappings = it } }

    androidx.compose.material3.Scaffold(topBar = { MasroofTopAppBar(title = "مرسلو الرسائل والمؤسسات", onBack = onClose) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            Text("حدد البنك أو المؤسسة التي تتبع لها كل مرسل. سُستخدم هذه المعلومة في الرسائل المستقبلية فقط.", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text("إضافة تعيين جديد", style = FinancialTypography.merchant)
                    OutlinedTextField(value = newSender, onValueChange = { newSender = it; errorMessage = null }, label = { Text("رقم المرسل أو اسمه") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newInstitution, onValueChange = { newInstitution = it; errorMessage = null }, label = { Text("اسم البنك أو المؤسسة") }, modifier = Modifier.fillMaxWidth())
                    errorMessage?.let { Text(it, style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.padding(top = Spacing.x2)) {
                        TextButton(onClick = {
                            if (newSender.isBlank() || newInstitution.isBlank()) errorMessage = "أدخل اسم المرسل والمؤسسة"
                            else scope.launch {
                                repo.upsert(newSender.trim(), newInstitution.trim())
                                newSender = ""
                                newInstitution = ""
                            }
                        }) { Text("تذكر هذا المرسل لهذه المؤسسة مستقبلًا") }
                        TextButton(onClick = { newSender = ""; newInstitution = ""; errorMessage = null }) { Text("إلغاء") }
                    }
                }
            }
            Text("التعيينات المحفوظة", style = FinancialTypography.merchant, modifier = Modifier.padding(top = Spacing.x4))
            if (mappings.isEmpty()) Text("لا توجد تعيينات بعد", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                items(mappings, key = { it.id }) { mapping ->
                    MappingRow(mapping,
                        onToggleActive = { active -> scope.launch { repo.setActive(mapping.id, active) } },
                        onEdit = { editState = EditState(mapping.senderKey, mapping.institutionName) },
                        onDelete = { scope.launch { repo.delete(mapping.id) } }
                    )
                }
            }
        }
    }

    editState?.let { state ->
        EditMappingDialog(
            state = state,
            onDismiss = { editState = null },
            onConfirm = { newInstitution ->
                scope.launch { repo.upsert(state.senderKey, newInstitution); editState = null }
            },
        )
    }
}

@Composable
private fun MappingRow(mapping: SenderInstitutionMappingEntity, onToggleActive: (Boolean) -> Unit, onEdit: (SenderInstitutionMappingEntity) -> Unit, onDelete: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = FinancialShapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mapping.senderKey, style = FinancialTypography.merchant)
                Text(if (mapping.isActive) "نشط" else "معطل", style = FinancialTypography.badge, color = if (mapping.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text(mapping.institutionName, style = FinancialTypography.metadata)
            Text("تم التأكيد ${mapping.confirmationCount} مرة", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("آخر تأكيد: ${formatTimestamp(mapping.lastConfirmedAt)}", style = FinancialTypography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.padding(top = Spacing.x2)) {
                TextButton(onClick = { onToggleActive(!mapping.isActive) }) { Text(if (mapping.isActive) "تعطيل" else "تفعيل") }
                TextButton(onClick = { onEdit(mapping) }) { Text("تعديل المؤسسة") }
                TextButton(onClick = onDelete) { Text("حذف") }
            }
        }
    }
}

private data class EditState(val senderKey: String, val institution: String)

@Composable
private fun EditMappingDialog(state: EditState, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var institution by remember { mutableStateOf(state.institution) }
    var pickerExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المؤسسة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("المرسل: ${state.senderKey}", style = FinancialTypography.metadata)
                OutlinedTextField(value = institution, onValueChange = { institution = it }, label = { Text("المؤسسة") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickerExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("اختر من المؤسسات المعروفة") }
                DropdownMenu(expanded = pickerExpanded, onDismissRequest = { pickerExpanded = false }) {
                    FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS.forEach { known ->
                        DropdownMenuItem(text = { Text(known) }, onClick = { institution = known; pickerExpanded = false })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (institution.isNotBlank()) onConfirm(institution.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun formatTimestamp(ts: Long): String {
    if (ts == 0L) return "—"
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale("ar"))
    return fmt.format(Date(ts))
}
