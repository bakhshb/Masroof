package com.baraa.masroof.parsing.model

/**
 * Dual representation of an SMS body after generic normalization.
 *
 * [originalBody] is preserved unchanged for traceability.
 * [normalizedBody] is the working text for classification/extraction.
 * [comparisonBody] is a lowercase Latin-shadow form for case-insensitive matching.
 */
data class NormalizedSms(
    val originalBody: String,
    val normalizedBody: String,
    val comparisonBody: String,
)
