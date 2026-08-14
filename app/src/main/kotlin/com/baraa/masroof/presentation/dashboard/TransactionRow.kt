package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate

@Composable
fun TransactionRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = MasroofIcons.transactionType(row.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.title ?: transactionTypeLabel(row.type), style = MaterialTheme.typography.titleSmall)
                    Text(formatLocalizedMoney(row.amount))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(transactionTypeLabel(row.type), style = MaterialTheme.typography.bodySmall)
                    row.cardLast4?.let { last4 ->
                        Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatLocalizedTransactionDate(row.localDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (directionIcon, directionLabelRes) = when (row.direction) {
                        TransactionDirectionUi.OUTWARD -> MasroofIcons.externalOut to R.string.dashboard_direction_out
                        TransactionDirectionUi.INWARD -> MasroofIcons.refunds to R.string.dashboard_direction_in
                        TransactionDirectionUi.INCOME -> MasroofIcons.income to R.string.dashboard_direction_income
                        TransactionDirectionUi.TRANSFER_IN -> MasroofIcons.externalIn to R.string.dashboard_direction_transfer_in
                        TransactionDirectionUi.NEUTRAL -> MasroofIcons.selfTransfer to R.string.dashboard_direction_neutral
                    }
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(directionLabelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun transactionTypeLabel(type: FinancialTransactionType): String =
    stringResource(
        when (type) {
            FinancialTransactionType.EXPENSE -> R.string.txn_type_expense
            FinancialTransactionType.INCOME -> R.string.txn_type_income
            FinancialTransactionType.SELF_TRANSFER -> R.string.txn_type_self_transfer
            FinancialTransactionType.EXTERNAL_TRANSFER_IN -> R.string.txn_type_external_in
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> R.string.txn_type_external_out
            FinancialTransactionType.CREDIT_CARD_PAYMENT -> R.string.txn_type_card_payment
            FinancialTransactionType.REFUND -> R.string.txn_type_refund
            FinancialTransactionType.CASH_WITHDRAWAL -> R.string.txn_type_cash_withdrawal
            FinancialTransactionType.BILL_PAYMENT -> R.string.txn_type_bill_payment
            FinancialTransactionType.FEE -> R.string.txn_type_fee
            FinancialTransactionType.ADJUSTMENT -> R.string.txn_type_adjustment
            FinancialTransactionType.UNKNOWN -> R.string.txn_type_unknown
        },
    )
