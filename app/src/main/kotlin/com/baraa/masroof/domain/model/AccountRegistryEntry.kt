package com.baraa.masroof.domain.model

/**
 * Durable account-container knowledge in the ownership registry.
 *
 * Identity: [bank] + [maskedNumber].
 */
data class AccountRegistryEntry(
    val bank: Bank,
    val maskedNumber: String,
    val ownership: OwnershipStatus,
    val displayName: String? = null,
    val accountType: AccountType = AccountType.CURRENT,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
)
