package com.baraa.masroof.domain.model

/**
 * Payment card associated with a bank, optionally linked to an [Account].
 */
data class Card(
    override val id: String,
    override val bank: Bank,
    val last4: String?,
    override val displayName: String?,
    override val ownership: OwnershipStatus,
    val type: CardType,
    val linkedAccountId: String?,
) : FinancialContainer
