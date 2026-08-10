package com.baraa.masroof.parsing.model

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence

/**
 * Result of bank detection from sender/body evidence.
 */
sealed interface BankDetectionResult {
    data class Detected(
        val bank: Bank,
        val confidence: Confidence,
        val evidence: List<String>,
    ) : BankDetectionResult

    data class Unknown(
        val reasons: List<String> = emptyList(),
    ) : BankDetectionResult
}
