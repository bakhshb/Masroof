package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.ui.TransactionTypeVisuals
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.MoneyValue
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as MasroofApplication
    val transaction by app.transactionRepository.observeById(transactionId)
        .collectAsStateWithLifecycle(initialValue = null)
    val accounts by app.financialAccountRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by app.categoryRepository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { MasroofTopAppBar("تفاصيل العملية", onBack = onBack) }) { padding ->
        val tx = transaction
        if (tx == null) {
            Text("العملية غير موجودة", Modifier.padding(padding).padding(Spacing.x4))
            return@Scaffold
        }
        val source = accounts.firstOrNull { it.id == tx.sourceAccountId }
        val destination = accounts.firstOrNull { it.id == tx.destinationAccountId }
        val category = categories.firstOrNull { it.id == tx.categoryId }
        val unresolved = tx.needsReview || tx.accountLinkNeedsReview ||
            tx.postingStatus == TransactionPostingStatus.NEEDS_REVIEW

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            item {
                tx.amount?.let {
                    MoneyValue(
                        value = it,
                        label = tx.currency.name,
                        emphasize = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            detailItem("الطرف", tx.merchantOrBeneficiary)
            detailItem("نوع العملية", TransactionTypeVisuals.label(tx.transactionType))
            detailItem("المعالجة المالية", ReviewClassification.treatmentLabel(tx.financialTreatment))
            detailItem("التاريخ والوقت", financialDateTime(tx))
            detailItem("حساب المصدر", source?.displayName)
            detailItem("حساب الوجهة", destination?.displayName)
            detailItem("المؤسسة أو المرسل", tx.originalSender)
            detailItem(
                "المعرّف",
                tx.accountOrCardLastFourDigits?.takeLast(4)?.let { "••••$it" },
            )
            detailItem("التصنيف", category?.nameAr)
            detailItem("الحالة", postingLabel(tx))
            tx.exclusionReason?.takeIf { it.isNotBlank() }?.let {
                detailItem("سبب الاستبعاد أو التكرار", it)
            }
            item {
                when {
                    unresolved -> PrimaryButton(
                        label = "إكمال المراجعة",
                        onClick = onOpenReview,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    tx.postingStatus == TransactionPostingStatus.POSTED ||
                        tx.postingStatus == TransactionPostingStatus.REVERSED -> PrimaryButton(
                        label = "تصحيح",
                        onClick = {
                            scope.launch {
                                val reopened = withContext(Dispatchers.IO) {
                                    app.transactionCorrectionService.reopenForCorrection(tx)
                                }
                                if (reopened is com.baraa.masroof.ledger.CorrectionResult.Success) {
                                    onOpenReview()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.detailItem(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    item {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun financialDateTime(tx: TransactionEntity): String {
    val locale = Locale("ar")
    val date = tx.transactionDate
        ?: Instant.ofEpochMilli(tx.smsTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = tx.transactionTime
        ?: Instant.ofEpochMilli(tx.smsTimestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    return "${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}، " +
        time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
}

private fun postingLabel(tx: TransactionEntity): String = when {
    tx.postingStatus == TransactionPostingStatus.POSTED -> "مُرحّلة"
    tx.postingStatus == TransactionPostingStatus.REVERSED -> "مصححة"
    tx.needsReview || tx.accountLinkNeedsReview ||
        tx.postingStatus == TransactionPostingStatus.NEEDS_REVIEW -> "تحتاج مراجعة"
    else -> "جاهزة"
}
