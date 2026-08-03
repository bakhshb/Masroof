package com.baraa.masroof.ui.merchants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.data.repository.CategoryRepository
import com.baraa.masroof.data.repository.MerchantMemoryRepository
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMemoryScreen(
    onClose: () -> Unit,
    onShowFeedback: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val memoryRepo: MerchantMemoryRepository = app.merchantMemoryRepository
    val categoryRepo: CategoryRepository = app.categoryRepository
    val scope = rememberCoroutineScope()

    var memories by remember { mutableStateOf<List<MerchantMemory>>(emptyList()) }
    var categories by remember { mutableStateOf<List<com.baraa.masroof.data.db.Category>>(emptyList()) }
    var editing by remember { mutableStateOf<MerchantMemory?>(null) }
    var pendingDelete by remember { mutableStateOf<MerchantMemory?>(null) }

    LaunchedEffect(Unit) {
        memoryRepo.observeAll().collectLatest { memories = it }
    }
    LaunchedEffect(Unit) {
        categoryRepo.observeAll().collectLatest { categories = it }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.merchants_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (memories.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = memories, key = { it.normalizedKey }) { mem ->
                        MerchantRow(
                            memory = mem,
                            categoryName = categories.firstOrNull { it.id == mem.preferredCategoryId }?.nameAr,
                            onClick = { editing = mem },
                            onToggleEnabled = { enabled ->
                                scope.launch { memoryRepo.setEnabled(mem.normalizedKey, enabled) }
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { mem ->
        MerchantMemoryEditDialog(
            memory = mem,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { newCategoryId, newTreatment ->
                scope.launch {
                    memoryRepo.remember(
                        rawMerchant = mem.displayName,
                        displayName = mem.displayName,
                        categoryId = newCategoryId,
                        treatment = newTreatment,
                    )
                    editing = null
                    onShowFeedback("تم تحديث قاعدة التاجر")
                }
            },
            onDelete = {
                pendingDelete = mem
                editing = null
            },
        )
    }

    pendingDelete?.let { mem ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(id = R.string.merchant_delete_rule)) },
            text = { Text(stringResource(id = R.string.category_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        memoryRepo.delete(mem.normalizedKey)
                        pendingDelete = null
                    }
                }) {
                    Text(
                        text = stringResource(id = R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MerchantRow(
    memory: MerchantMemory,
    categoryName: String?,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (memory.enabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memory.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (memory.enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(id = R.string.merchant_count_label, memory.confirmationCount),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    memory.preferredCategoryId?.let { _ ->
                        Text(
                            text = categoryName ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatDate(memory.lastConfirmedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = memory.enabled, onCheckedChange = onToggleEnabled)
            }
            if (!memory.enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.merchant_disabled_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.merchants_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.merchants_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantMemoryEditDialog(
    memory: MerchantMemory,
    categories: List<com.baraa.masroof.data.db.Category>,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long?, treatment: FinancialTreatment?) -> Unit,
    onDelete: () -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf(memory.preferredCategoryId) }
    var selectedTreatment by remember { mutableStateOf(memory.preferredFinancialTreatment ?: FinancialTreatment.EXPENSE) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var treatmentExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.merchant_edit_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = memory.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                ) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.nameAr ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.category_field_name_ar)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("—") },
                            onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            },
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.nameAr) },
                                onClick = {
                                    selectedCategoryId = c.id
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
                // Treatment dropdown
                ExposedDropdownMenuBox(
                    expanded = treatmentExpanded,
                    onExpandedChange = { treatmentExpanded = !treatmentExpanded },
                ) {
                    OutlinedTextField(
                        value = treatmentLabel(selectedTreatment),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.tx_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = treatmentExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = treatmentExpanded,
                        onDismissRequest = { treatmentExpanded = false },
                    ) {
                        FinancialTreatment.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(treatmentLabel(t)) },
                                onClick = {
                                    selectedTreatment = t
                                    treatmentExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedCategoryId, selectedTreatment) }) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(id = R.string.merchant_delete_rule),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        },
    )
}

private fun treatmentLabel(t: FinancialTreatment): String = when (t) {
    FinancialTreatment.EXPENSE -> "مصروف"
    FinancialTreatment.INCOME -> "دخل"
    FinancialTreatment.INTERNAL_TRANSFER -> "تحويل داخلي"
    FinancialTreatment.CREDIT_CARD_PAYMENT -> "سداد بطاقة"
    FinancialTreatment.INVESTMENT -> "استثمار"
    FinancialTreatment.REFUND -> "استرداد"
    FinancialTreatment.BANK_FEE -> "رسوم"
    FinancialTreatment.CASH_WITHDRAWAL -> "سحب نقدي"
    FinancialTreatment.PENDING_REVIEW -> "يحتاج مراجعة"
    FinancialTreatment.IGNORED -> "مهمل"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val fmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return runCatching { fmt.format(Date(timestamp)) }.getOrDefault("")
}
