package com.baraa.masroof.presentation.review

import com.baraa.masroof.R

object ReviewReasonLabels {
    fun labelRes(reason: String): Int? =
        when (reason) {
            "transfer_pending_match" -> R.string.review_reason_transfer_pending_match
            "bill_payment_financial_treatment_unresolved" ->
                R.string.review_reason_bill_payment
            "unknown_message_family" -> R.string.review_reason_unknown_family
            "missing_amount" -> R.string.review_reason_missing_amount
            "needs_review" -> R.string.review_reason_needs_review
            "purchase_instrument_ownership_unknown" ->
                R.string.review_reason_purchase_ownership_unknown
            "purchase_without_resolved_owned_instrument" ->
                R.string.review_reason_purchase_unresolved
            "card_payment_missing_containers" -> R.string.review_reason_card_payment_missing
            "card_payment_ownership_unresolved" -> R.string.review_reason_card_payment_ownership
            "transfer_ownership_unknown_no_guess" -> R.string.review_reason_transfer_ownership
            "transfer_missing_source_or_destination" -> R.string.review_reason_transfer_missing_side
            "non_financial_or_informational_message" -> R.string.review_reason_non_financial
            else -> null
        }
}
