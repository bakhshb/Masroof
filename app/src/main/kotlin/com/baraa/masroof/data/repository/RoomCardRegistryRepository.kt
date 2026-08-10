package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.CardRegistryDao
import com.baraa.masroof.data.room.entity.CardRegistryEntity
import com.baraa.masroof.data.room.mapper.RegistryMapper
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.RegistryIdentity
import com.baraa.masroof.domain.repository.CardRegistryRepository

class RoomCardRegistryRepository(
    private val dao: CardRegistryDao,
) : CardRegistryRepository {
    override suspend fun observe(reference: CardReference, rawSmsId: String) {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return

        val bankId = reference.bank.id
        dao.insertIfAbsent(
            CardRegistryEntity(
                bankId = bankId,
                last4 = last4,
                ownershipStatus = OwnershipStatus.UNKNOWN.name,
                firstSeenRawSmsId = rawSmsId,
                lastSeenRawSmsId = rawSmsId,
            ),
        )
        dao.touchObservation(bankId, last4, rawSmsId)
    }

    override suspend fun setOwnership(reference: CardReference, status: OwnershipStatus) {
        RegistryIdentity.requireKnownBank(reference.bank, "CardRegistry.setOwnership")
        val last4 = reference.last4?.trim().orEmpty()
        require(last4.isNotEmpty()) { "last4 required to set ownership" }

        dao.upsertOwnership(reference.bank.id, last4, status.name)
    }

    override suspend fun resolve(reference: CardReference): OwnershipStatus {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return OwnershipStatus.UNKNOWN
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return OwnershipStatus.UNKNOWN
        val entry = dao.get(reference.bank.id, last4) ?: return OwnershipStatus.UNKNOWN
        return OwnershipStatus.valueOf(entry.ownershipStatus)
    }

    override suspend fun get(reference: CardReference): CardRegistryEntry? {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return null
        val last4 = reference.last4?.trim().orEmpty()
        if (last4.isEmpty()) return null
        return dao.get(reference.bank.id, last4)?.let(RegistryMapper::toCardEntry)
    }

    override suspend fun listAll(): List<CardRegistryEntry> =
        dao.listAll().map(RegistryMapper::toCardEntry)
}
