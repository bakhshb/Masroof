package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.AccountRegistryDao
import com.baraa.masroof.data.room.entity.AccountRegistryEntity
import com.baraa.masroof.data.room.mapper.RegistryMapper
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.ownership.RegistryIdentity
import com.baraa.masroof.domain.repository.AccountRegistryRepository

class RoomAccountRegistryRepository(
    private val dao: AccountRegistryDao,
) : AccountRegistryRepository {
    override suspend fun observe(reference: AccountReference, rawSmsId: String) {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return

        val bankId = reference.bank.id
        // Atomic create-if-absent as UNKNOWN. Never overwrites ownership on conflict.
        dao.insertIfAbsent(
            AccountRegistryEntity(
                bankId = bankId,
                maskedNumber = masked,
                ownershipStatus = OwnershipStatus.UNKNOWN.name,
                firstSeenRawSmsId = rawSmsId,
                lastSeenRawSmsId = rawSmsId,
            ),
        )
        // Observation metadata only — ownershipStatus is untouched.
        dao.touchObservation(bankId, masked, rawSmsId)
    }

    override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) {
        RegistryIdentity.requireKnownBank(reference.bank, "AccountRegistry.setOwnership")
        val masked = reference.maskedNumber?.trim().orEmpty()
        require(masked.isNotEmpty()) { "maskedNumber required to set ownership" }

        // Single atomic UPSERT: discovery cannot leave UNKNOWN after this wins,
        // and observation metadata is preserved on conflict.
        dao.upsertOwnership(reference.bank.id, masked, status.name)
    }

    override suspend fun resolve(reference: AccountReference): OwnershipStatus {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return OwnershipStatus.UNKNOWN
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return OwnershipStatus.UNKNOWN
        val entry = dao.get(reference.bank.id, masked) ?: return OwnershipStatus.UNKNOWN
        return OwnershipStatus.valueOf(entry.ownershipStatus)
    }

    override suspend fun get(reference: AccountReference): AccountRegistryEntry? {
        if (!RegistryIdentity.isKnownBank(reference.bank)) return null
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return null
        return dao.get(reference.bank.id, masked)?.let(RegistryMapper::toAccountEntry)
    }

    override suspend fun listAll(): List<AccountRegistryEntry> =
        dao.listAll().map(RegistryMapper::toAccountEntry)
}
