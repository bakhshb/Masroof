package com.baraa.masroof.bank

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.parsing.model.BankDetectionResult

/**
 * Routes incoming SMS to the first registered bank adapter that detects a match.
 *
 * Registration order is the tie-break when more than one adapter returns [BankDetectionResult.Detected].
 */
class BankSmsRegistry(
    private val adapters: List<BankSmsAdapter>,
) {
    fun route(sender: String, body: String): BankRoutingResult {
        var unmatchedReason: String? = null
        for (adapter in adapters) {
            when (val detection = adapter.detect(sender, body)) {
                is BankDetectionResult.Detected ->
                    return BankRoutingResult.Matched(adapter, detection)
                is BankDetectionResult.Unknown ->
                    unmatchedReason = detection.reasons.firstOrNull() ?: unmatchedReason
            }
        }
        return BankRoutingResult.NotMatched(
            reason = unmatchedReason ?: "sender_not_in_scope",
        )
    }

    fun adapterFor(bank: Bank): BankSmsAdapter? =
        adapters.firstOrNull { it.bank == bank }

    fun singleAdapterOrNull(): BankSmsAdapter? =
        adapters.singleOrNull()
}
