package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.transaction.TransactionReclassificationService
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.review.ReviewReasonLabels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: TransactionPreviewUi,
    reclassifying: Boolean,
    reclassifySuccess: Boolean,
    error: String?,
    onBack: () -> Unit,
    onReclassify: (FinancialTransactionType) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transaction_detail_title)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.review_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransactionRow(transaction)
            Text(
                stringResource(R.string.transaction_detail_current_type, stringResource(transaction.type.toUiLabelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionHeader(
                title = stringResource(R.string.transaction_detail_reclassify_title),
                icon = MasroofIcons.reviewQueue,
            )
            Text(
                stringResource(R.string.transaction_detail_reclassify_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RECLASSIFY_TYPE_OPTIONS.forEach { type ->
                val isCurrent = type == transaction.type
                IconTextButton(
                    onClick = { onReclassify(type) },
                    enabled = !reclassifying && !isCurrent,
                    icon = MasroofIcons.transactionType(type),
                    text = if (isCurrent) {
                        stringResource(R.string.transaction_detail_current_option, stringResource(type.toUiLabelRes()))
                    } else {
                        stringResource(type.toUiLabelRes())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (reclassifySuccess) {
                Text(stringResource(R.string.transaction_detail_reclassify_success), color = MaterialTheme.colorScheme.primary)
            }
            error?.let { reason ->
                Text(stringResource(R.string.transaction_detail_reclassify_failed), color = MaterialTheme.colorScheme.error)
                val detailRes = ReviewReasonLabels.labelRes(reason)
                Text(
                    detailRes?.let { stringResource(it) } ?: reason,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (reclassifying) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private val RECLASSIFY_TYPE_OPTIONS: List<FinancialTransactionType> =
    TransactionReclassificationService.ALLOWED_TYPES.toList().sortedBy { it.name }

private fun FinancialTransactionType.toUiLabelRes(): Int =
    when (this) {
        FinancialTransactionType.EXPENSE -> R.string.txn_type_expense
        FinancialTransactionType.INCOME -> R.string.txn_type_income
        FinancialTransactionType.SELF_TRANSFER -> R.string.txn_type_self_transfer
        FinancialTransactionType.EXTERNAL_TRANSFER_IN -> R.string.txn_type_external_in
        FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> R.string.txn_type_external_out
        FinancialTransactionType.CREDIT_CARD_PAYMENT -> R.string.txn_type_card_payment
        FinancialTransactionType.REFUND -> R.string.txn_type_refund
        FinancialTransactionType.CASH_WITHDRAWAL -> R.string.txn_type_cash_withdrawal
        FinancialTransactionType.FEE -> R.string.txn_type_fee
        FinancialTransactionType.ADJUSTMENT -> R.string.txn_type_adjustment
        FinancialTransactionType.UNKNOWN -> R.string.txn_type_unknown
    }
