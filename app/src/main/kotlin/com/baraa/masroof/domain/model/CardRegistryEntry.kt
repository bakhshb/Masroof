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
    val displayName: String? = null,
    val cardNetwork: CardNetwork? = null,
    val cardType: CardType? = null,
    val linkedAccountBankId: String? = null,
    val linkedAccountMaskedNumber: String? = null,
    val parentCardLast4: String? = null,
    val cardRole: CardRole? = null,
    val creditFacilityId: String? = null,
    val firstSeenRawSmsId: String?,
    val lastSeenRawSmsId: String?,
) {
    val linkedAccount: AccountReference?
        get() {
            val bankId = linkedAccountBankId ?: return null
            val masked = linkedAccountMaskedNumber ?: return null
            return AccountReference(Bank(bankId), masked)
        }
}
