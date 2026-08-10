package com.baraa.masroof.domain.model

/**
 * Durable account-container knowledge in the ownership registry.
 *
 * Distinct from a parse-time [AccountReference] observation and from a fully
 * populated [Account] (which requires type/display fields discovery may not know).
 *
 * Identity: [bank] + [maskedNumber].
 */
data class AccountRegistryEntry(
    val bank: Bank,
    val maskedNumber: String,
    val ownership: OwnershipStatus,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
)
