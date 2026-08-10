package com.baraa.masroof.domain.model

/**
 * Parse / classification confidence with human-readable supporting reasons.
 *
 * Numeric thresholds for automatic vs review decisions are intentionally not
 * encoded here (DOMAIN §6); they belong to later tested policy.
 */
data class Confidence(
    val score: Double,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(score in 0.0..1.0) {
            "Confidence score must be in [0.0, 1.0], was $score"
        }
    }
}
