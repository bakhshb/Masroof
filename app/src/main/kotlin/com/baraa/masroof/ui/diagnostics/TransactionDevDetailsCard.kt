package com.baraa.masroof.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType

/**
 * "تفاصيل التشخيص" card shown inside the edit dialog when the
 * developer "إظهار تفاصيل التشخيص" toggle is enabled.
 *
 * **Never** includes:
 *  - the original SMS body
 *  - the merchant name (we surface a short tag derived from the
 *    normalized key, not the display name)
 *  - exact amounts (we show a coarse bucket label, not the value)
 *  - card / account digits
 *  - API keys
 */
@Composable
fun TransactionDevDetailsCard(entity: TransactionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.dev_details_title),
                style = MaterialTheme.typography.titleSmall,
            )
            DevRow(
                label = stringResource(R.string.dev_details_parser_name),
                value = "auto-detected",
            )
            DevRow(
                label = stringResource(R.string.dev_details_parser_version),
                value = "v1",
            )
            DevRow(
                label = stringResource(R.string.dev_details_confidence),
                value = "${entity.confidence}%",
            )
            DevRow(
                label = stringResource(R.string.dev_details_date_source),
                value = entity.dateSource.name,
            )
            DevRow(
                label = stringResource(R.string.dev_details_duplicate_status),
                value = if (entity.userConfirmed) "user-confirmed" else "pending",
            )
            DevRow(
                label = stringResource(R.string.dev_details_financial_rule),
                value = entity.financialTreatment.name,
            )
            DevRow(
                label = stringResource(R.string.dev_details_category_source),
                value = entity.categorySource.name,
            )
            DevRow(
                label = stringResource(R.string.dev_details_missing_fields),
                value = if (entity.merchantOrBeneficiary.isNullOrBlank()) "merchant" else "none",
            )
            DevRow(
                label = stringResource(R.string.dev_details_ai_eligibility),
                value = aiEligibility(entity),
            )
        }
    }
}

@Composable
private fun DevRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun aiEligibility(e: TransactionEntity): String {
    val eligibleType = e.transactionType in ELIGIBLE_TYPES
    val eligibleTreatment = e.financialTreatment == FinancialTreatment.EXPENSE ||
        e.financialTreatment == FinancialTreatment.BANK_FEE
    val eligibleStatus = e.status != TransactionStatus.DECLINED &&
        e.status != TransactionStatus.PENDING
    return when {
        eligibleType && eligibleTreatment && eligibleStatus -> "eligible"
        else -> "ineligible"
    }
}

private val ELIGIBLE_TYPES = setOf(
    TransactionType.PURCHASE,
    TransactionType.ONLINE_PURCHASE,
    TransactionType.CASH_WITHDRAWAL,
    TransactionType.FEE,
    TransactionType.OTHER_FINANCIAL,
)