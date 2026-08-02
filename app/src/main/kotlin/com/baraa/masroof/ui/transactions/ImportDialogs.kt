package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.repository.DuplicateDecision
import com.baraa.masroof.data.repository.ImportPreview
import com.baraa.masroof.data.repository.ImportPreviewItem
import com.baraa.masroof.data.repository.ImportSummary
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -- formatting helpers (kept private; not @Composable so they can be called
//    from inside AlertDialog text lambdas safely) --------------------------

private val TX_DATE_FORMATTER: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

private fun formatShortDate(timestamp: Long): String =
    if (timestamp <= 0L) "" else runCatching { TX_DATE_FORMATTER.format(Date(timestamp)) }.getOrDefault("")

private fun formatShortLine(
    amount: BigDecimal?,
    currency: Currency,
    type: TransactionType,
    smsTimestamp: Long,
): String {
    val a = amount?.toPlainString() ?: "—"
    val c = currency.shortLabel()
    val t = type.shortLabel()
    val d = formatShortDate(smsTimestamp)
    return "$t · $a $c · $d"
}

private fun Currency.shortLabel(): String = when (this) {
    Currency.SAR -> "SAR"
    Currency.USD -> "USD"
    Currency.EUR -> "EUR"
    Currency.UNKNOWN -> "?"
}

private fun TransactionType.shortLabel(): String = when (this) {
    TransactionType.PURCHASE -> "شراء"
    TransactionType.ONLINE_PURCHASE -> "شراء أونلاين"
    TransactionType.CASH_WITHDRAWAL -> "سحب نقدي"
    TransactionType.TRANSFER_OUT -> "تحويل صادر"
    TransactionType.TRANSFER_IN -> "تحويل وارد"
    TransactionType.CARD_PAYMENT -> "سداد بطاقة"
    TransactionType.REFUND -> "استرداد"
    TransactionType.SALARY -> "راتب"
    TransactionType.DEPOSIT -> "إيداع"
    TransactionType.BANK_FEE -> "رسوم"
    TransactionType.INTERNAL_TRANSFER -> "تحويل داخلي"
    TransactionType.INVESTMENT_TRANSFER -> "تحويل استثماري"
    TransactionType.DECLINED -> "مرفوضة"
    TransactionType.UNKNOWN -> "غير معروف"
}

// -- Dialogs ----------------------------------------------------------------

/**
 * Pre-import confirmation dialog. Shows the 6 counts (scanned, parsed,
 * unparseable, new, exact-duplicates, possible-duplicates) and asks the
 * user to confirm.
 */
@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReviewDuplicates: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.import_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(id = R.string.import_preview_scanned, preview.messagesScanned))
                Text(stringResource(id = R.string.import_preview_parsed, preview.parsedSuccessfully))
                Text(stringResource(id = R.string.import_preview_unparseable, preview.unparseable))
                Text(stringResource(id = R.string.import_preview_new, preview.newTransactions))
                Text(stringResource(id = R.string.import_preview_exact_dups, preview.exactDuplicates))
                Text(
                    stringResource(
                        id = R.string.import_preview_possible_dups,
                        preview.possibleDuplicates,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = preview.hasAnythingToImport,
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (preview.hasPossibleDuplicates) {
                    TextButton(onClick = onReviewDuplicates) {
                        Text(stringResource(id = R.string.import_preview_review_duplicates))
                    }
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * Dialog listing the possible-duplicate items. For each one the user picks
 * INSERT_ANYWAY (add despite the collision) or SKIP (discard).
 */
@Composable
fun DuplicateReviewDialog(
    items: List<ImportPreviewItem>,
    onDecisions: (Map<Int, DuplicateDecision>) -> Unit,
    onCancel: () -> Unit,
) {
    val decisions = remember {
        mutableStateMapOf<Int, DuplicateDecision>().apply {
            items.forEach { put(it.smsIndex, it.decision) }
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.duplicate_review_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        id = R.string.duplicate_review_body,
                        items.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = items, key = { it.smsIndex }) { item ->
                        DuplicateReviewRow(
                            item = item,
                            currentDecision = decisions[item.smsIndex] ?: item.decision,
                            onChange = { decisions[item.smsIndex] = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecisions(decisions.toMap()) }) {
                Text(stringResource(id = R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DuplicateReviewRow(
    item: ImportPreviewItem,
    currentDecision: DuplicateDecision,
    onChange: (DuplicateDecision) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(id = R.string.import_possible_dup),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.merchant ?: item.bodyExcerpt.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatShortLine(item.amount, item.currency, item.transactionType, item.smsTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.collidingWith?.let { existing ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        id = R.string.duplicate_review_existing_sender,
                        existing.originalSender ?: "",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(
                        id = R.string.duplicate_review_existing_amount,
                        existing.amount?.toPlainString() ?: "",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(
                        id = R.string.duplicate_review_existing_date,
                        formatShortDate(existing.smsTimestamp),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onChange(DuplicateDecision.INSERT_ANYWAY) }) {
                    val isSelected = currentDecision == DuplicateDecision.INSERT_ANYWAY
                    Text(
                        text = stringResource(id = R.string.duplicate_decision_insert),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onChange(DuplicateDecision.SKIP) }) {
                    val isSelected = currentDecision == DuplicateDecision.SKIP
                    Text(
                        text = stringResource(id = R.string.duplicate_decision_skip),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Post-import summary dialog. */
@Composable
fun ImportSummaryDialog(
    summary: ImportSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.import_summary_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(id = R.string.import_summary_inserted, summary.inserted))
                if (summary.possibleDuplicatesInserted > 0) {
                    Text("تمت إضافة ${summary.possibleDuplicatesInserted} من المحتمل تكرارها")
                }
                Text("تم تجاهل ${summary.exactDuplicatesSkipped + summary.possibleDuplicatesSkipped} عمليات مكررة")
                Text(stringResource(id = R.string.import_summary_unparseable, summary.unparseable))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_continue))
            }
        },
    )
}
