package com.baraa.masroof.domain.model

/**
 * Durable card-container knowledge in the ownership registry.
 *
 * Identity: [bank] + [last4].
 */
data class CardRegistryEntry(
    val bank: Bank,
    val last4: String,
    val ownership: OwnershipStatus,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
)
