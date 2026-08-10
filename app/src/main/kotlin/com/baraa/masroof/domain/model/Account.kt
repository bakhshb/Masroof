package com.baraa.masroof.domain.model

/**
 * Bank account / wallet / similar cash container owned or known to Masroof.
 */
data class Account(
    override val id: String,
    override val bank: Bank,
    val maskedNumber: String?,
    override val displayName: String?,
    override val ownership: OwnershipStatus,
    val type: AccountType,
) : FinancialContainer
