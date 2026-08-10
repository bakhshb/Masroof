package com.baraa.masroof.domain.model

/**
 * Parse-time reference to a card mentioned in an SMS.
 *
 * Identity is bank-scoped (DOMAIN §9).
 */
data class CardReference(
    val bank: Bank,
    val last4: String?,
)
