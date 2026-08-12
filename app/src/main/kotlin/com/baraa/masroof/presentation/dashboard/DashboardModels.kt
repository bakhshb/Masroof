package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.application.dashboard.MonthlyFinancialSummary
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod

data class UnknownCardCandidateUi(
    val bank: Bank,
    val last4: String,
)

enum class TransactionDirectionUi {
    OUTWARD,
    INWARD,
    INCOME,
    TRANSFER_IN,
    NEUTRAL,
}

data class TransactionPreviewUi(
    val id: String,
    /** Merchant/counterparty when present; Compose falls back to Arabic type label when null. */
    val title: String?,
    val amount: Money,
    val amountLabel: String,
    val dateLabel: String,
    val type: FinancialTransactionType,
    val typeLabelResHint: FinancialTransactionType,
    val direction: TransactionDirectionUi,
    val cardLast4: String?,
    /** Lowercase merchant/counterparty text for in-memory search. */
    val searchText: String,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val period: FinancialPeriod? = null,
    val periodLabel: String = "",
    val periodAdjustmentHint: String? = null,
    val summary: MonthlyFinancialSummary? = null,
    val creditCards: CreditCardsOverview? = null,
    val recentTransactions: List<TransactionPreviewUi> = emptyList(),
    val allTransactions: List<TransactionPreviewUi> = emptyList(),
    val isCurrentPeriod: Boolean = true,
    val error: DashboardError? = null,
    val rescanning: Boolean = false,
    val rescanStatus: SmsRescanStatus? = null,
    val reparsingStored: Boolean = false,
    val selectedTransactionId: String? = null,
    val reclassifying: Boolean = false,
    val reclassifyError: String? = null,
    val reclassifySuccess: Boolean = false,
    val unknownCards: List<UnknownCardCandidateUi> = emptyList(),
    val ownershipUpdating: Boolean = false,
)

enum class DashboardError {
    LOAD_FAILED,
}

enum class SmsRescanStatus {
    OK,
    NO_MESSAGES,
    NO_BANK_SMS,
    NO_TRANSACTIONS,
    FAILED,
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
            -> TransactionDirectionUi.INCOME

            FinancialTransactionType.EXTERNAL_TRANSFER_IN,
            -> TransactionDirectionUi.TRANSFER_IN

            FinancialTransactionType.REFUND,
            -> TransactionDirectionUi.INWARD

            FinancialTransactionType.SELF_TRANSFER,
            FinancialTransactionType.ADJUSTMENT,
            FinancialTransactionType.UNKNOWN,
            -> TransactionDirectionUi.NEUTRAL
        }
}
