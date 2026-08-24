package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType

@Composable
fun TransactionRow(
    row: TransactionPreviewUi,
    modifier: Modifier = Modifier,
    ownedCards: List<OwnedCardUi> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    DashboardRecentTransactionRow(
        row = row,
        modifier = modifier,
        ownedCards = ownedCards,
        onClick = onClick,
    )
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
