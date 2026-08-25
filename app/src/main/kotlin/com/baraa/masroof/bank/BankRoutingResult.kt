package com.baraa.masroof.bank

import com.baraa.masroof.parsing.model.BankDetectionResult

/**
 * Outcome of routing one SMS to a registered bank adapter.
 */
sealed interface BankRoutingResult {
    data class Matched(
        val adapter: BankSmsAdapter,
        val detection: BankDetectionResult.Detected,
    ) : BankRoutingResult

    data class NotMatched(
        val reason: String,
    ) : BankRoutingResult
}
