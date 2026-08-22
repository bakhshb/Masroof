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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.IconTextButtonOutlined
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.SectionHeader
import com.baraa.masroof.presentation.common.ShareActionIcon
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate
import com.baraa.masroof.presentation.review.ReviewReasonLabels
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: TransactionPreviewUi,
    smsEvidence: List<TransactionSmsEvidenceUi>,
    smsLoading: Boolean,
    reclassifying: Boolean,
    reclassifySuccess: Boolean,
    ignoring: Boolean,
    error: String?,
    onBack: () -> Unit,
    onReclassify: (FinancialTransactionType) -> Unit,
    onIgnore: () -> Unit,
    ownedCards: List<OwnedCardUi> = emptyList(),
) {
    var showReclassifySheet by rememberSaveable { mutableStateOf(false) }
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showIgnoreConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val actionInProgress = reclassifying || ignoring

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

    if (showIgnoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!actionInProgress) showIgnoreConfirmDialog = false
            },
            icon = {
                Icon(
                    imageVector = MasroofIcons.warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.transaction_detail_ignore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transaction.title?.let { title ->
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.transaction_detail_ignore_confirm_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIgnore()
                        showIgnoreConfirmDialog = false
                    },
                    enabled = !actionInProgress,
                ) {
                    Text(stringResource(R.string.transaction_detail_ignore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showIgnoreConfirmDialog = false },
                    enabled = !actionInProgress,
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (showConfirmDialog && pendingTypeEnum != null && pendingTypeEnum != transaction.type) {
        AlertDialog(
            onDismissRequest = {
                if (!actionInProgress) showConfirmDialog = false
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
                    enabled = !actionInProgress,
                ) {
                    Text(stringResource(R.string.transaction_reclassify_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !actionInProgress,
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
                if (!actionInProgress) {
                    showReclassifySheet = false
                    pendingType = null
                }
            },
            onSelectType = { type -> pendingType = type.name },
            onSave = { showConfirmDialog = true },
        )
    }

    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.transaction_share)
    val shareSubject = stringResource(R.string.transaction_detail_title)
    val shareText = transactionDetailShareText(transaction)

    val extended = MasroofThemeExtras.extendedColors
    MasroofSecondaryScaffold(
        title = stringResource(R.string.transaction_detail_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.review_back),
        actions = {
            ShareActionIcon(
                enabled = true,
                onClick = {
                    SharePlainText.share(
                        context = context,
                        text = shareText,
                        chooserTitle = shareChooserTitle,
                        subject = shareSubject,
                    )
                },
            )
        },
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MasroofCard {
                Text(
                    transaction.title ?: transactionTypeLabel(transaction.type),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    formatLocalizedMoney(transaction.amount),
                    style = MaterialTheme.typography.headlineMedium,
                    color = when (transaction.direction) {
                        TransactionDirectionUi.INCOME,
                        TransactionDirectionUi.INWARD,
                        TransactionDirectionUi.TRANSFER_IN,
                        -> extended.inflow
                        TransactionDirectionUi.OUTWARD -> extended.outflow
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionHeader(
                title = stringResource(R.string.transaction_detail_info_section),
                icon = MasroofIcons.recentTransactions,
            )
            MasroofCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconLabelRow(
                        icon = MasroofIcons.transactionType(transaction.type),
                        label = stringResource(R.string.transaction_detail_type),
                        trailing = transactionTypeLabel(transaction.type),
                    )
                    IconLabelRow(
                        icon = MasroofIcons.calendar,
                        label = stringResource(R.string.transaction_detail_date),
                        trailing = formatLocalizedTransactionDate(transaction.localDate),
                    )
                    IconLabelRow(
                        icon = TransactionDirectionPresentation.icon(transaction.direction),
                        label = stringResource(R.string.transaction_detail_direction),
                        trailing = stringResource(TransactionDirectionPresentation.labelRes(transaction.direction)),
                    )
                    transaction.cardLast4?.let { last4 ->
                        val cardLabel = cardDisplayLabelFromTransaction(
                            row = transaction,
                            cards = ownedCards,
                        )
                        cardLabel?.let { label ->
                            IconLabelRow(
                                icon = MasroofIcons.cardPayment,
                                label = stringResource(R.string.transaction_detail_card),
                                trailing = label,
                            )
                        }
                    }
                    transaction.title?.let { title ->
                        IconLabelRow(
                            icon = MasroofIcons.merchant,
                            label = stringResource(R.string.transaction_detail_merchant),
                            trailing = title,
                        )
                    }
                    transaction.sarEquivalent?.let { sar ->
                        IconLabelRow(
                            icon = MasroofIcons.income,
                            label = stringResource(R.string.transaction_detail_sar_equivalent),
                            trailing = formatLocalizedMoney(sar),
                        )
                    }
                    transaction.appliedExchangeRate?.let { rate ->
                        IconLabelRow(
                            icon = MasroofIcons.externalOut,
                            label = stringResource(
                                R.string.transaction_detail_exchange_rate,
                                transaction.amount.currency.name,
                            ),
                            trailing = rate.stripTrailingZeros().toPlainString(),
                        )
                    }
                    transaction.exchangeRateSource?.let { source ->
                        IconLabelRow(
                            icon = MasroofIcons.reviewQueue,
                            label = stringResource(R.string.transaction_detail_exchange_rate_source),
                            trailing = stringResource(ExchangeRateSourcePresentation.labelRes(source)),
                        )
                    }
                }
            }

            if (smsLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (smsEvidence.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.transaction_detail_sms_section),
                    icon = MasroofIcons.sms,
                )
                smsEvidence.forEachIndexed { index, evidence ->
                    if (smsEvidence.size > 1) {
                        Text(
                            stringResource(
                                R.string.transaction_detail_sms_index,
                                index + 1,
                                smsEvidence.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    evidence.sender?.let { sender ->
                        IconLabelRow(
                            icon = MasroofIcons.sender,
                            label = stringResource(R.string.review_sender, sender),
                        )
                    }
                    MasroofCard {
                        Text(
                            evidence.body,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    pendingType = null
                    showReclassifySheet = true
                },
                enabled = !actionInProgress,
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

            IconTextButtonOutlined(
                onClick = { showIgnoreConfirmDialog = true },
                enabled = !actionInProgress,
                icon = MasroofIcons.warning,
                text = stringResource(R.string.review_action_ignore),
                modifier = Modifier.fillMaxWidth(),
            )

            if (reclassifySuccess) {
                Text(
                    stringResource(R.string.transaction_detail_reclassify_success),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let { reason ->
                val isIgnoreError = reason in IGNORE_ERROR_REASONS
                Text(
                    stringResource(
                        if (isIgnoreError) {
                            R.string.transaction_detail_ignore_failed
                        } else {
                            R.string.transaction_detail_reclassify_failed
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
                val detailRes = ReviewReasonLabels.labelRes(reason)
                Text(
                    detailRes?.let { stringResource(it) } ?: reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (actionInProgress) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private val IGNORE_ERROR_REASONS = setOf(
    "delete_failed",
    "review_resolution_failed",
)

