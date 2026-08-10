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

        // IGNORE-insert as UNKNOWN; never overwrites ownership on conflict.
        dao.observeAtomic(
            entity = AccountRegistryEntity(
                bankId = reference.bank.id,
                maskedNumber = masked,
                ownershipStatus = OwnershipStatus.UNKNOWN.name,
                firstSeenRawSmsId = rawSmsId,
                lastSeenRawSmsId = rawSmsId,
            ),
            rawSmsId = rawSmsId,
        )
    }

    override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) {
        RegistryIdentity.requireKnownBank(reference.bank, "AccountRegistry.setOwnership")
        val masked = reference.maskedNumber?.trim().orEmpty()
        require(masked.isNotEmpty()) { "maskedNumber required to set ownership" }

        // Confirmation-before-observation may create a row with null seen metadata.
        dao.setOwnershipAtomic(
            entity = AccountRegistryEntity(
                bankId = reference.bank.id,
                maskedNumber = masked,
                ownershipStatus = status.name,
                firstSeenRawSmsId = null,
                lastSeenRawSmsId = null,
            ),
            ownershipStatus = status.name,
        )
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
