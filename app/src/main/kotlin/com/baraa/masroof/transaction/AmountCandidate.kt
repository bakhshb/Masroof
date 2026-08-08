package com.baraa.masroof.transaction

import java.math.BigDecimal

/**
 * One monetary value observed in an SMS, with its semantic role.
 *
 * Selection rule: [ParsedTransaction.amount] may only come from candidates
 * whose [semanticRole] is [MonetaryRole.TRANSACTION_AMOUNT]. Magnitude,
 * position, or currency proximity alone must never decide the amount.
 */
data class AmountCandidate(
    val value: BigDecimal,
    val currency: Currency,
    val semanticRole: MonetaryRole,
    val label: String,
    val evidence: String,
    val confidence: Int,
    val exclusionReason: String? = null,
    /** Retained for older diagnostics that keyed off pattern id / source. */
    val sourcePattern: String = evidence,
    val originalTextRange: IntRange = 0..0,
    val precedingContext: String = label,
    val followingContext: String = "",
    val parsedValue: BigDecimal? = value,
)
