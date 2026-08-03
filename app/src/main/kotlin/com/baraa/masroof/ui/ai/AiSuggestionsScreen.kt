package com.baraa.masroof.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.baraa.masroof.ai.AiSuggestionRepository.QueueFilter
import com.baraa.masroof.data.db.AiSuggestion
import com.baraa.masroof.data.db.Category
import kotlinx.coroutines.launch

/**
 * اقتراحات التصنيف الذكي — review queue. Shows AI suggestions that
 * are awaiting user action. Newest first. Supports filtering by:
 *  - جميع الاقتراحات (PENDING)
 *  - ثقة مرتفعة (confidence ≥ threshold)
 *  - ثقة منخفضة (confidence < threshold)
 *  - مرفوضة (REJECTED)
 *
 * Per-item actions: قبول / تعديل / رفض. The raw AI response is never
 * exposed — only sanitized fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSuggestionsScreen(
    onClose: () -> Unit,
    minimumConfidence: Int,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()
    val repo = app.aiSuggestionRepository

    var filter by remember { mutableStateOf(QueueFilter.ALL) }
    val items by repo.observeFiltered(filter, minimumConfidence)
        .collectAsState(initial = emptyList())
    val allCategories by app.categoryRepository.observeAll()
        .collectAsState(initial = emptyList())

    var pendingModify by remember { mutableStateOf<Long?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.ai_suggestions_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == QueueFilter.ALL,
                    onClick = { filter = QueueFilter.ALL },
                    label = { Text(stringResource(R.string.ai_suggestions_filter_all)) },
                )
                FilterChip(
                    selected = filter == QueueFilter.HIGH,
                    onClick = { filter = QueueFilter.HIGH },
                    label = { Text(stringResource(R.string.ai_suggestions_filter_high)) },
                )
                FilterChip(
                    selected = filter == QueueFilter.LOW,
                    onClick = { filter = QueueFilter.LOW },
                    label = { Text(stringResource(R.string.ai_suggestions_filter_low)) },
                )
                FilterChip(
                    selected = filter == QueueFilter.REJECTED,
                    onClick = { filter = QueueFilter.REJECTED },
                    label = { Text(stringResource(R.string.ai_suggestions_filter_rejected)) },
                )
            }
            HorizontalDivider()
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(id = R.string.ai_suggestions_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            minimumConfidence = minimumConfidence,
                            onAccept = {
                                scope.launch {
                                    val ok = repo.accept(suggestion.id)
                                    toast = if (ok) {
                                        context.getString(R.string.ai_suggestions_accepted_toast)
                                    } else {
                                        context.getString(R.string.ai_suggestions_category_disabled)
                                    }
                                }
                            },
                            onReject = {
                                scope.launch {
                                    repo.reject(suggestion.id)
                                    toast = context.getString(R.string.ai_suggestions_rejected_toast)
                                }
                            },
                            onModify = { pendingModify = suggestion.id },
                        )
                    }
                }
            }
        }
        toast?.let { msg ->
            AlertDialog(
                onDismissRequest = { toast = null },
                confirmButton = {
                    TextButton(onClick = { toast = null }) { Text("حسنًا") }
                },
                title = null,
                text = { Text(msg) },
            )
        }
        pendingModify?.let { id ->
            ModifyCategoryDialog(
                categories = allCategories.filter { it.enabled },
                onDismiss = { pendingModify = null },
                onConfirm = { newCategory ->
                    scope.launch {
                        val ok = repo.modify(
                            suggestionId = id,
                            newCategoryId = newCategory.id,
                            newCategoryName = newCategory.nameAr,
                        )
                        toast = if (ok) {
                            context.getString(R.string.ai_suggestions_modified_toast)
                        } else {
                            context.getString(R.string.ai_suggestions_category_disabled)
                        }
                        pendingModify = null
                    }
                },
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: AiSuggestion,
    minimumConfidence: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onModify: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = suggestion.merchantDisplay.ifBlank { "(بدون اسم)" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${suggestion.amountBucket} ${suggestion.currency}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "${stringResource(R.string.ai_suggestion_confidence, suggestion.confidence)}  •  " +
                    confidenceLabel(suggestion.confidence, minimumConfidence),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${suggestion.categoryName}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (suggestion.explanation.isNotBlank()) {
                Text(
                    text = stringResource(R.string.ai_suggestions_explanation, suggestion.explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Expandable details — provider + model only.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = if (expanded) "إخفاء التفاصيل" else "تفاصيل المزود")
                }
            }
            if (expanded) {
                Text(
                    text = stringResource(
                        R.string.ai_suggestions_provider_model,
                        suggestion.providerName, suggestion.modelName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_suggestions_accept))
                }
                OutlinedButton(onClick = onModify, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_suggestions_modify))
                }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_suggestions_reject))
                }
            }
        }
    }
}

/**
 * Arabic confidence labels per the spec's three-tier rule.
 *  - ≥ HIGH_THRESHOLD → ثقة مرتفعة
 *  - MIDDLE range    → ثقة متوسطة
 *  - < MIDDLE        → ثقة منخفضة
 *
 * Threshold boundaries:
 *  - HIGH_THRESHOLD = max(minimumConfidence, 80)
 *  - LOW_THRESHOLD = min(minimumConfidence, 50)
 */
internal fun confidenceLabel(confidence: Int, minimumConfidence: Int): String {
    val high = maxOf(minimumConfidence, 80)
    val low = minOf(minimumConfidence, 50)
    return when {
        confidence >= high -> "ثقة مرتفعة"
        confidence >= low -> "ثقة متوسطة"
        else -> "ثقة منخفضة"
    }
}

@Composable
private fun ModifyCategoryDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit,
) {
    var selected by remember { mutableStateOf(categories.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_suggestions_select_category)) },
        text = {
            Column {
                categories.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selected?.id == c.id,
                            onClick = { selected = c },
                        )
                        Text(text = c.nameAr, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}