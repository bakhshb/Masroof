package com.baraa.masroof.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.ai.AiCategorizationOutcome
import com.baraa.masroof.ai.AiCategorizationResult
import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.data.db.TransactionEntity
import kotlinx.coroutines.launch

/**
 * Per-transaction AI suggestion. Composable used inside the edit dialog.
 *
 * Behavior:
 *  - Shows a "طلب اقتراح تصنيف" button when the transaction is eligible.
 *  - Hides the button (and the section) for ineligible transactions:
 *    internal transfers, card payments, refunds, declined/pending,
 *    income, investments, transactions already covered by a user-
 *    confirmed merchant memory.
 *  - When clicked, builds a sanitized request via
 *    [AiCategorizationService.buildRequest] and calls
 *    [AiCategorizationService.categorize].
 *  - Shows the result with confidence + a short explanation.
 *  - Accept applies the category, marks `userConfirmed`, and offers to
 *    remember the merchant.
 *  - Reject marks the cache entry as rejected so the same provider is
 *    not asked again immediately for the same merchant.
 *  - Modify lets the user pick a different category.
 */
@Composable
fun AiPerTransactionSection(
    entity: TransactionEntity,
    merchantMemories: List<MerchantMemory>,
    onApplied: (TransactionEntity) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()

    val aiEnabled by app.aiSettingsRepository.run {
        kotlinx.coroutines.flow.MutableStateFlow(
            kotlinx.coroutines.runBlocking { load() }.enabled
        )
    }.collectAsState(initial = false)

    val minimumConfidence by app.aiSettingsRepository.run {
        kotlinx.coroutines.flow.MutableStateFlow(
            kotlinx.coroutines.runBlocking { load() }.minimumConfidence
        )
    }.collectAsState(initial = 80)

    // Eligibility: EXPENSE / BANK_FEE only, no card payments / internal
    // transfers / refunds / declined / pending.
    val eligibleType = entity.transactionType in ELIGIBLE_TYPES
    val eligibleTreatment = entity.financialTreatment == com.baraa.masroof.transaction.FinancialTreatment.EXPENSE ||
        entity.financialTreatment == com.baraa.masroof.transaction.FinancialTreatment.BANK_FEE
    val eligibleStatus = entity.status != com.baraa.masroof.transaction.TransactionStatus.DECLINED &&
        entity.status != com.baraa.masroof.transaction.TransactionStatus.PENDING
    val merchantKey = com.baraa.masroof.transaction.MerchantNormalizer.normalize(entity.merchantOrBeneficiary)
    val hasMemory = merchantMemories.any { it.normalizedKey == merchantKey && it.confirmationCount >= 1 && it.enabled }
    val eligible = aiEnabled && eligibleType && eligibleTreatment && eligibleStatus && !hasMemory

    var result by remember { mutableStateOf<AiCategorizationResult?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var modifyOpen by remember { mutableStateOf(false) }

    Column {
        if (!aiEnabled) {
            // AI is disabled — show nothing per spec.
            return@Column
        }
        if (!eligible) {
            // Ineligible — show a short reason so the user understands.
            Text(
                text = ineligibleReason(
                    eligibleType, eligibleTreatment, eligibleStatus, hasMemory,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (result == null && !loading && failureMessage == null) {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        try {
                            val req = app.aiCategorizationService().buildRequest(
                                merchant = entity.merchantOrBeneficiary.orEmpty(),
                                type = entity.transactionType,
                                amount = entity.amount,
                                currency = entity.currency,
                                categories = app.categoryRepository.getAll(),
                                includeExactAmount = false,
                            )
                            val outcome = app.aiCategorizationService()
                                .categorize(entity.merchantOrBeneficiary.orEmpty(), req)
                            result = (outcome as? AiCategorizationOutcome.Success)?.result
                            if (result == null) failureMessage = context.getString(R.string.ai_suggestion_provider_unavailable)
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.ai_request_suggestion)) }
        }
        if (loading) {
            Text(text = stringResource(R.string.ai_settings_test_in_progress), style = MaterialTheme.typography.bodySmall)
        }
        result?.let { r ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.ai_suggestion_section_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = r.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.ai_suggestion_confidence, r.confidence),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (r.confidence < minimumConfidence) {
                        Text(
                            text = stringResource(R.string.ai_suggestion_low_confidence_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (r.explanation.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.ai_suggestions_explanation, r.explanation),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    // Expandable provider / model details.
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(text = if (expanded) "إخفاء التفاصيل" else "تفاصيل المزود")
                    }
                    if (expanded) {
                        Text(
                            text = stringResource(R.string.ai_suggestions_provider_model, r.providerName, r.modelName),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val updated = entity.copy(
                                    categoryId = r.categoryId,
                                    categorySource = com.baraa.masroof.transaction.CategorySource.AI,
                                    categoryConfidence = r.confidence,
                                    userConfirmed = true,
                                    needsReview = false,
                                    updatedAt = System.currentTimeMillis(),
                                )
                                scope.launch {
                                    app.transactionRepository.update(updated)
                                    app.aiCategorizationService().accept(merchantKey)
                                    onApplied(updated)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.ai_suggestions_accept)) }
                        OutlinedButton(
                            onClick = { modifyOpen = true },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.ai_suggestions_modify)) }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.aiCategorizationService().reject(merchantKey)
                                    result = null
                                    failureMessage = context.getString(R.string.ai_suggestions_rejected_toast)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.ai_suggestions_reject)) }
                    }
                }
            }
        }
        failureMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (modifyOpen) {
            ModifyPicker(
                categories = app.categoryRepository.observeAll().collectAsState(initial = emptyList()).value.filter { it.enabled },
                onDismiss = { modifyOpen = false },
                onPick = { picked ->
                    val updated = entity.copy(
                        categoryId = picked.id,
                        categorySource = com.baraa.masroof.transaction.CategorySource.USER,
                        categoryConfidence = 100,
                        userConfirmed = true,
                        needsReview = false,
                        updatedAt = System.currentTimeMillis(),
                    )
                    scope.launch {
                        app.transactionRepository.update(updated)
                        modifyOpen = false
                        onApplied(updated)
                    }
                },
            )
        }
    }
}

private fun ineligibleReason(
    typeOk: Boolean,
    treatmentOk: Boolean,
    statusOk: Boolean,
    hasMemory: Boolean,
): String {
    if (hasMemory) return "محفوظ في ذاكرة التجار"
    if (!statusOk) return "لا يصلح للاقتراح: العملية معلقة أو مرفوضة"
    if (!treatmentOk) return "لا يصلح للاقتراح: نوع المعالجة غير مؤهل"
    if (!typeOk) return "لا يصلح للاقتراح: نوع العملية غير مؤهل"
    return "غير مؤهل"
}

@Composable
private fun ModifyPicker(
    categories: List<com.baraa.masroof.data.db.Category>,
    onDismiss: () -> Unit,
    onPick: (com.baraa.masroof.data.db.Category) -> Unit,
) {
    var selected by remember { mutableStateOf(categories.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_suggestions_select_category)) },
        text = {
            Column {
                categories.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
            TextButton(onClick = { selected?.let(onPick) }, enabled = selected != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val ELIGIBLE_TYPES = setOf(
    com.baraa.masroof.transaction.TransactionType.PURCHASE,
    com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE,
    com.baraa.masroof.transaction.TransactionType.CASH_WITHDRAWAL,
    com.baraa.masroof.transaction.TransactionType.FEE,
    com.baraa.masroof.transaction.TransactionType.OTHER_FINANCIAL,
)