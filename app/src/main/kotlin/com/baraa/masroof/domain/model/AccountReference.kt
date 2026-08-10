package com.baraa.masroof.domain.model

/**
 * Parse-time reference to an account mentioned in an SMS.
 *
 * Identity is bank-scoped: last digits alone are not globally unique
 * (DOMAIN §9).
 */
data class AccountReference(
    val bank: Bank,
    val maskedNumber: String?,
)
