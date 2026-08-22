package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus

/**
 * Durable card ownership registry. Discovery observes; confirmation sets status.
 */
interface CardRegistryRepository {
    suspend fun observe(reference: CardReference, rawSmsId: String)

    suspend fun setOwnership(reference: CardReference, status: OwnershipStatus)

    suspend fun resolve(reference: CardReference): OwnershipStatus

    suspend fun get(reference: CardReference): CardRegistryEntry?

    suspend fun listAll(): List<CardRegistryEntry>

    suspend fun updateDisplayName(reference: CardReference, displayName: String?)

    suspend fun updateCardNetwork(reference: CardReference, network: CardNetwork?)

    suspend fun updateCardType(reference: CardReference, cardType: CardType?)

    suspend fun linkDebitToAccount(card: CardReference, account: AccountReference)

    suspend fun setPrimaryCard(reference: CardReference)

    suspend fun setSupplementaryCard(reference: CardReference, primaryLast4: String)

    suspend fun clearCardRole(reference: CardReference)
}
