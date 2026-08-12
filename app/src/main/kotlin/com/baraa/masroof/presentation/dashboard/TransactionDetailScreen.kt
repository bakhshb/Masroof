package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.formatCardLast4
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
    var showReclassifySheet by rememberSaveable { mutableStateOf(false) }
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val pendingTypeEnum = pendingType?.let { name ->
        runCatching { FinancialTransactionType.valueOf(name) }.getOrNull()
    }

    LaunchedEffect(reclassifySuccess) {
        if (reclassifySuccess) {
            showReclassifySheet = false
            showConfirmDialog = false
            pendingType = null
        }
    }

    if (showConfirmDialog && pendingTypeEnum != null && pendingTypeEnum != transaction.type) {
        AlertDialog(
            onDismissRequest = {
                if (!reclassifying) showConfirmDialog = false
            },
            icon = {
                Icon(
                    imageVector = MasroofIcons.reviewQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.transaction_reclassify_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transaction.title?.let { title ->
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(
                            R.string.transaction_reclassify_confirm_body,
                            transactionTypeLabel(transaction.type),
                            transactionTypeLabel(pendingTypeEnum),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReclassify(pendingTypeEnum)
                        showConfirmDialog = false
                    },
                    enabled = !reclassifying,
                ) {
                    Text(stringResource(R.string.transaction_reclassify_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !reclassifying,
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (showReclassifySheet) {
        TransactionReclassifyBottomSheet(
            currentType = transaction.type,
            selectedType = pendingTypeEnum,
            saving = reclassifying,
            onDismiss = {
                if (!reclassifying) {
                    showReclassifySheet = false
                    pendingType = null
                }
            },
            onSelectType = { type -> pendingType = type.name },
            onSave = { showConfirmDialog = true },
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                transaction.title ?: transactionTypeLabel(transaction.type),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                transaction.amountLabel,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SectionHeader(
                title = stringResource(R.string.transaction_detail_info_section),
                icon = MasroofIcons.recentTransactions,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconLabelRow(
                        icon = MasroofIcons.transactionType(transaction.type),
                        label = stringResource(R.string.transaction_detail_type),
                        trailing = transactionTypeLabel(transaction.type),
                    )
                    IconLabelRow(
                        icon = MasroofIcons.calendar,
                        label = stringResource(R.string.transaction_detail_date),
                        trailing = transaction.dateLabel,
                    )
                    IconLabelRow(
                        icon = directionIcon(transaction.direction),
                        label = stringResource(R.string.transaction_detail_direction),
                        trailing = stringResource(directionLabelRes(transaction.direction)),
                    )
                    transaction.cardLast4?.let { last4 ->
                        IconLabelRow(
                            icon = MasroofIcons.cardPayment,
                            label = stringResource(R.string.transaction_detail_card),
                            trailing = stringResource(
                                R.string.dashboard_credit_card_last4,
                                formatCardLast4(last4),
                            ),
                        )
                    }
                    transaction.title?.let { title ->
                        IconLabelRow(
                            icon = MasroofIcons.merchant,
                            label = stringResource(R.string.transaction_detail_merchant),
                            trailing = title,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    pendingType = null
                    showReclassifySheet = true
                },
                enabled = !reclassifying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MasroofIcons.reviewQueue,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.transaction_detail_edit_category))
            }

            if (reclassifySuccess) {
                Text(
                    stringResource(R.string.transaction_detail_reclassify_success),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let { reason ->
                Text(
                    stringResource(R.string.transaction_detail_reclassify_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                val detailRes = ReviewReasonLabels.labelRes(reason)
                Text(
                    detailRes?.let { stringResource(it) } ?: reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (reclassifying) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun directionIcon(direction: TransactionDirectionUi) =
    when (direction) {
        TransactionDirectionUi.OUTWARD -> MasroofIcons.externalOut
        TransactionDirectionUi.INWARD -> MasroofIcons.refunds
        TransactionDirectionUi.INCOME -> MasroofIcons.income
        TransactionDirectionUi.TRANSFER_IN -> MasroofIcons.externalIn
        TransactionDirectionUi.NEUTRAL -> MasroofIcons.selfTransfer
    }

private fun directionLabelRes(direction: TransactionDirectionUi): Int =
    when (direction) {
        TransactionDirectionUi.OUTWARD -> R.string.dashboard_direction_out
        TransactionDirectionUi.INWARD -> R.string.dashboard_direction_in
        TransactionDirectionUi.INCOME -> R.string.dashboard_direction_income
        TransactionDirectionUi.TRANSFER_IN -> R.string.dashboard_direction_transfer_in
        TransactionDirectionUi.NEUTRAL -> R.string.dashboard_direction_neutral
    }
