package com.baraa.masroof.testsupport

import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.CardRegistryRepository

open class NoOpCardRegistryRepository(
    private val entries: List<CardRegistryEntry> = emptyList(),
) : CardRegistryRepository {
    override suspend fun observe(reference: CardReference, rawSmsId: String) = Unit

    override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) = Unit

    override suspend fun resolve(reference: CardReference): OwnershipStatus = OwnershipStatus.UNKNOWN

    override suspend fun get(reference: CardReference): CardRegistryEntry? =
        entries.find { it.bank == reference.bank && it.last4 == reference.last4 }

    override suspend fun listAll(): List<CardRegistryEntry> = entries

    override suspend fun updateDisplayName(reference: CardReference, displayName: String?) = Unit

    override suspend fun updateCardNetwork(reference: CardReference, network: CardNetwork?) = Unit

    override suspend fun updateCardType(reference: CardReference, cardType: CardType?) = Unit

    override suspend fun linkDebitToAccount(card: CardReference, account: AccountReference) = Unit

    override suspend fun setPrimaryCard(reference: CardReference) = Unit

    override suspend fun setSupplementaryCard(reference: CardReference, primaryLast4: String) = Unit

    override suspend fun clearCardRole(reference: CardReference) = Unit
}
