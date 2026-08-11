package com.baraa.masroof.presentation.review

import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ReviewKind

data class ReviewListItemUi(
    val id: String,
    val kind: ReviewKind,
    val kindLabelRes: Int,
    val title: String,
    val amountLabel: String?,
    val dateLabel: String,
    val reasonLabel: String,
    val dismissibleAsNonFinancial: Boolean = false,
)

data class ReviewDetailUi(
    val id: String,
    val kind: ReviewKind,
    val kindLabelRes: Int,
    val sender: String?,
    val body: String,
    val dateLabel: String,
    val messageFamilyLabel: String?,
    val messageFamily: MessageFamily?,
    val amountLabel: String?,
    val merchant: String?,
    val counterparty: String?,
    val reasonLabels: List<String>,
    val pairCandidates: List<ReviewListItemUi>,
    val showExternalTransferAction: Boolean,
    val showIncomingIncomeAction: Boolean,
    val showFinancialTypeActions: Boolean,
    val showDismissNonFinancialAction: Boolean,
)

data class ReviewUiState(
    val loading: Boolean = true,
    val items: List<ReviewListItemUi> = emptyList(),
    val informationalDismissCount: Int = 0,
    val selectedDetail: ReviewDetailUi? = null,
    val resolving: Boolean = false,
    val error: ReviewError? = null,
    val actionErrorDetail: String? = null,
    val message: ReviewMessage? = null,
)

enum class ReviewError {
    LOAD_FAILED,
    ACTION_FAILED,
}

enum class ReviewMessage {
    RESOLVED,
    STILL_NEEDS_REVIEW,
}

val REVIEW_FINANCIAL_TYPE_ACTIONS: List<FinancialTransactionType> = listOf(
    FinancialTransactionType.EXPENSE,
    FinancialTransactionType.INCOME,
    FinancialTransactionType.CREDIT_CARD_PAYMENT,
    FinancialTransactionType.REFUND,
    FinancialTransactionType.CASH_WITHDRAWAL,
    FinancialTransactionType.FEE,
)

fun MessageFamily?.toUiLabelRes(): Int? =
    when (this) {
        MessageFamily.PURCHASE -> com.baraa.masroof.R.string.review_family_purchase
        MessageFamily.TRANSFER_IN -> com.baraa.masroof.R.string.review_family_transfer_in
        MessageFamily.TRANSFER_OUT -> com.baraa.masroof.R.string.review_family_transfer_out
        MessageFamily.BILL_PAYMENT -> com.baraa.masroof.R.string.review_family_bill_payment
        MessageFamily.CARD_PAYMENT -> com.baraa.masroof.R.string.review_family_card_payment
        MessageFamily.REFUND -> com.baraa.masroof.R.string.review_family_refund
        MessageFamily.WITHDRAWAL -> com.baraa.masroof.R.string.review_family_withdrawal
        MessageFamily.FEE -> com.baraa.masroof.R.string.review_family_fee
        MessageFamily.UNKNOWN -> com.baraa.masroof.R.string.review_family_unknown
        else -> null
    }

fun FinancialTransactionType.toUiLabelRes(): Int =
    when (this) {
        FinancialTransactionType.EXPENSE -> com.baraa.masroof.R.string.txn_type_expense
        FinancialTransactionType.INCOME -> com.baraa.masroof.R.string.txn_type_income
        FinancialTransactionType.CREDIT_CARD_PAYMENT -> com.baraa.masroof.R.string.txn_type_card_payment
        FinancialTransactionType.REFUND -> com.baraa.masroof.R.string.txn_type_refund
        FinancialTransactionType.CASH_WITHDRAWAL -> com.baraa.masroof.R.string.txn_type_cash_withdrawal
        FinancialTransactionType.FEE -> com.baraa.masroof.R.string.txn_type_fee
        FinancialTransactionType.ADJUSTMENT -> com.baraa.masroof.R.string.txn_type_adjustment
        FinancialTransactionType.SELF_TRANSFER -> com.baraa.masroof.R.string.txn_type_self_transfer
        FinancialTransactionType.EXTERNAL_TRANSFER_IN -> com.baraa.masroof.R.string.txn_type_external_in
        FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> com.baraa.masroof.R.string.txn_type_external_out
        FinancialTransactionType.UNKNOWN -> com.baraa.masroof.R.string.txn_type_unknown
    }

fun ReviewKind.toUiLabelRes(): Int =
    when (this) {
        ReviewKind.NEEDS_REVIEW -> com.baraa.masroof.R.string.review_kind_needs_review
        ReviewKind.PENDING_MATCH -> com.baraa.masroof.R.string.review_kind_pending_match
    }

private val NON_FINANCIAL_BODY_MARKERS = listOf(
    "رمز التفعيل",
    "لإضافة المستفيد",
    "رمز التحقق",
    "تم تسجيل الدخول",
    "مكافآتي",
    "رصيد نقاطك",
    "برنامج مكاف",
)

fun looksLikeNonFinancialSms(body: String): Boolean =
    NON_FINANCIAL_BODY_MARKERS.any { body.contains(it) }

fun shouldOfferNonFinancialDismiss(
    messageFamily: MessageFamily?,
    reasons: List<String>,
    body: String,
): Boolean {
    if (messageFamily == MessageFamily.OTP || messageFamily == MessageFamily.NON_FINANCIAL) {
        return true
    }
    if (reasons.any { it == "non_financial_or_informational_message" }) {
        return true
    }
    return looksLikeNonFinancialSms(body)
}
