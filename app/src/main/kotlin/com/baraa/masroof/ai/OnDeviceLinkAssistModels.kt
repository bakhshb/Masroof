package com.baraa.masroof.ai

import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Owned-account snapshot for on-device link assist. Identifiers are last-4
 * only — never full account/card numbers.
 */
data class LinkAssistAccount(
    val id: Long,
    val displayName: String,
    val accountType: String,
    val identifierLast4s: List<String> = emptyList(),
)

/**
 * On-device-only input: SMS body stays on device and is never sent remotely.
 */
data class LinkAssistRequest(
    val smsBody: String,
    val sender: String?,
    val transactionType: String?,
    val amount: String?,
    val currency: String?,
    val transactionDate: String?,
    val lastFourEvidence: String?,
    val accounts: List<LinkAssistAccount>,
)

data class LinkAssistSuggestion(
    val treatment: FinancialTreatment,
    val sourceAccountId: Long?,
    val destinationAccountId: Long?,
    val confidence: Int,
    val reasonAr: String,
)

sealed class LinkAssistOutcome {
    data class Success(val suggestion: LinkAssistSuggestion) : LinkAssistOutcome()
    data class Failed(val reason: FailureReason, val messageAr: String? = null) : LinkAssistOutcome()
}
