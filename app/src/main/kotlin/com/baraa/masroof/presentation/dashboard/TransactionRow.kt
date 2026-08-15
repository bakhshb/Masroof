package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofMoneyRow
import com.baraa.masroof.presentation.common.MasroofMoneyRowStyle
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.locale.formatLocalizedMoney
import com.baraa.masroof.presentation.locale.formatLocalizedTransactionDate

@Composable
fun TransactionRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val title = row.title ?: transactionTypeLabel(row.type)
    val cardLabel = row.cardLast4?.let { last4 ->
        stringResource(R.string.dashboard_credit_card_last4, formatCardLast4(last4))
    }
    val subtitle = listOfNotNull(
        transactionTypeLabel(row.type),
        cardLabel,
        formatLocalizedTransactionDate(row.localDate),
    ).joinToString(" · ")
    val rowStyle = when {
        row.type == FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> MasroofMoneyRowStyle.Highlight
        row.direction == TransactionDirectionUi.INCOME ||
            row.direction == TransactionDirectionUi.INWARD ||
            row.direction == TransactionDirectionUi.TRANSFER_IN -> MasroofMoneyRowStyle.Inflow
        row.direction == TransactionDirectionUi.OUTWARD -> MasroofMoneyRowStyle.Outflow
        else -> MasroofMoneyRowStyle.Neutral
    }
    val amountPrefix = when (row.direction) {
        TransactionDirectionUi.INCOME,
        TransactionDirectionUi.INWARD,
        TransactionDirectionUi.TRANSFER_IN,
        -> "+"
        TransactionDirectionUi.OUTWARD -> "−"
        else -> ""
    }

    MasroofCard(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        MasroofMoneyRow(
            label = title,
            value = amountPrefix + formatLocalizedMoney(row.amount),
            style = rowStyle,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
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
