package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.MonthlyFinancialSummary
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod

enum class TransactionDirectionUi {
    OUTWARD,
    INWARD,
    NEUTRAL,
}

data class TransactionPreviewUi(
    val id: String,
    val title: String,
    val amountLabel: String,
    val dateLabel: String,
    val type: FinancialTransactionType,
    val typeLabelResHint: FinancialTransactionType,
    val direction: TransactionDirectionUi,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val period: FinancialPeriod? = null,
    val periodLabel: String = "",
    val summary: MonthlyFinancialSummary? = null,
    val recentTransactions: List<TransactionPreviewUi> = emptyList(),
    val isCurrentPeriod: Boolean = true,
    val error: DashboardError? = null,
)

enum class DashboardError {
    LOAD_FAILED,
}

object TransactionTypePresentation {
    fun direction(type: FinancialTransactionType): TransactionDirectionUi =
        when (type) {
            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.FEE,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.CASH_WITHDRAWAL,
            -> TransactionDirectionUi.OUTWARD

            FinancialTransactionType.INCOME,
            FinancialTransactionType.REFUND,
            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            -> TransactionDirectionUi.INWARD

            FinancialTransactionType.SELF_TRANSFER,
            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            -> TransactionDirectionUi.NEUTRAL
        }
}
