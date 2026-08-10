package com.baraa.masroof.parsing.model

import com.baraa.masroof.core.money.Money

/**
 * Provenance for a numeric money-like extraction.
 *
 * Enables V-001…V-007: validators can tell *why* a number was selected instead
 * of rejecting coincidental numeric equality with last4 / balances.
 */
data class AmountCandidate(
    val value: Money,
    val evidenceLabel: String,
    val sourceKind: AmountSourceKind,
    val confidence: Double = 1.0,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in [0.0, 1.0]" }
    }
}

enum class AmountSourceKind {
    TRANSACTION_AMOUNT,
    AVAILABLE_BALANCE,
    OUTSTANDING_BALANCE,
    CARD_LAST4,
    ACCOUNT_LAST4,
    REFERENCE,
    DATE_TIME,
    OTHER,
}
