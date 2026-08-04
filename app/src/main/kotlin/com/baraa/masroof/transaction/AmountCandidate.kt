package com.baraa.masroof.transaction

import java.math.BigDecimal

/** Evidence for a numeric amount candidate. Context is normalized and never logged with the SMS body. */
data class AmountCandidate(
    val parsedValue: BigDecimal?,
    val originalTextRange: IntRange,
    val currency: Currency,
    val precedingContext: String,
    val followingContext: String,
    val confidence: Int,
    val exclusionReason: String? = null,
    val sourcePattern: String,
)
