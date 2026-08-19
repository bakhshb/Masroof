package com.baraa.masroof.presentation.dashboard

import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.CreditCardsOverview
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.application.dashboard.CurrentAccountSummary
import com.baraa.masroof.application.dashboard.MonthlyFinancialSummary
import com.baraa.masroof.application.dashboard.SpendingSplitSummary
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import java.time.LocalDate

data class UnknownCardCandidateUi(
    val bank: Bank,
    val last4: String,
)

/** Confirmed owned card — can offer «stop tracking». */
data class OwnedCardUi(
    val bank: Bank,
    val last4: String,
)

data class OwnedAccountUi(
    val bank: Bank,
    val maskedNumber: String,
)

enum class TransactionDirectionUi {
    OUTWARD,
    INWARD,
    INCOME,
    TRANSFER_IN,
    NEUTRAL,
}

data class TransactionSmsEvidenceUi(
    val body: String,
    val sender: String?,
)

data class TransactionPreviewUi(
    val id: String,
    /** Merchant/counterparty when present; Compose falls back to Arabic type label when null. */
    val title: String?,
    val amount: Money,
    val localDate: LocalDate,
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
    val currentAccount: CurrentAccountSummary? = null,
    val spendingSplit: SpendingSplitSummary? = null,
    val creditCards: CreditCardsOverview? = null,
    val recentTransactions: List<TransactionPreviewUi> = emptyList(),
    val allTransactions: List<TransactionPreviewUi> = emptyList(),
    val isCurrentPeriod: Boolean = true,
    val error: DashboardError? = null,
    val rescanning: Boolean = false,
    val rescanStatus: SmsRescanStatus? = null,
    val reparsingStored: Boolean = false,
    val selectedTransactionId: String? = null,
    val selectedTransactionSms: List<TransactionSmsEvidenceUi> = emptyList(),
    val selectedTransactionSmsLoading: Boolean = false,
    val reclassifying: Boolean = false,
    val reclassifyError: String? = null,
    val reclassifySuccess: Boolean = false,
    val unknownCards: List<UnknownCardCandidateUi> = emptyList(),
    val ownedCards: List<OwnedCardUi> = emptyList(),
    val ownedAccounts: List<OwnedAccountUi> = emptyList(),
    val smsPermissionGranted: Boolean = true,
)

enum class DashboardError {
    LOAD_FAILED,
}

enum class SmsRescanStatus {
    OK,
    NO_MESSAGES,
    NO_BANK_SMS,
    NO_TRANSACTIONS,
    PERMISSION_DENIED,
    FAILED,
}

object TransactionDirectionPresentation {
    fun icon(direction: TransactionDirectionUi): ImageVector =
        when (direction) {
            TransactionDirectionUi.OUTWARD -> MasroofIcons.externalOut
            TransactionDirectionUi.INWARD -> MasroofIcons.refunds
            TransactionDirectionUi.INCOME -> MasroofIcons.income
            TransactionDirectionUi.TRANSFER_IN -> MasroofIcons.externalIn
            TransactionDirectionUi.NEUTRAL -> MasroofIcons.selfTransfer
        }

    fun labelRes(direction: TransactionDirectionUi): Int =
        when (direction) {
            TransactionDirectionUi.OUTWARD -> R.string.dashboard_direction_out
            TransactionDirectionUi.INWARD -> R.string.dashboard_direction_in
            TransactionDirectionUi.INCOME -> R.string.dashboard_direction_income
            TransactionDirectionUi.TRANSFER_IN -> R.string.dashboard_direction_transfer_in
            TransactionDirectionUi.NEUTRAL -> R.string.dashboard_direction_neutral
        }
}

object TransactionTypePresentation {
    fun direction(type: FinancialTransactionType): TransactionDirectionUi =
        when (type) {
            FinancialTransactionType.EXPENSE,
            FinancialTransactionType.FEE,
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
            FinancialTransactionType.CREDIT_CARD_PAYMENT,
            FinancialTransactionType.CASH_WITHDRAWAL,
            FinancialTransactionType.BILL_PAYMENT,
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
