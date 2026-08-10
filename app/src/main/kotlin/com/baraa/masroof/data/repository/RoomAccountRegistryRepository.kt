package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.AccountRegistryDao
import com.baraa.masroof.data.room.entity.AccountRegistryEntity
import com.baraa.masroof.data.room.mapper.RegistryMapper
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.AccountRegistryRepository

class RoomAccountRegistryRepository(
    private val dao: AccountRegistryDao,
) : AccountRegistryRepository {
    override suspend fun observe(reference: AccountReference, rawSmsId: String) {
        val bankId = reference.bank.id
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return

        val existing = dao.get(bankId, masked)
        if (existing == null) {
            dao.insert(
                AccountRegistryEntity(
                    bankId = bankId,
                    maskedNumber = masked,
                    ownershipStatus = OwnershipStatus.UNKNOWN.name,
                    firstSeenRawSmsId = rawSmsId,
                    lastSeenRawSmsId = rawSmsId,
                    evidenceCount = 1,
                ),
            )
            return
        }
        // Idempotent for the same SMS evidence; never rewrite ownership.
        if (existing.lastSeenRawSmsId == rawSmsId) return
        dao.touchEvidence(bankId, masked, rawSmsId)
    }

    override suspend fun setOwnership(reference: AccountReference, status: OwnershipStatus) {
        val bankId = reference.bank.id
        val masked = reference.maskedNumber?.trim().orEmpty()
        require(masked.isNotEmpty()) { "maskedNumber required to set ownership" }

        val existing = dao.get(bankId, masked)
        if (existing == null) {
            dao.insert(
                AccountRegistryEntity(
                    bankId = bankId,
                    maskedNumber = masked,
                    ownershipStatus = status.name,
                    firstSeenRawSmsId = null,
                    lastSeenRawSmsId = null,
                    evidenceCount = 0,
                ),
            )
        } else {
            dao.updateOwnership(bankId, masked, status.name)
        }
    }

    override suspend fun resolve(reference: AccountReference): OwnershipStatus {
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return OwnershipStatus.UNKNOWN
        val entry = dao.get(reference.bank.id, masked) ?: return OwnershipStatus.UNKNOWN
        return OwnershipStatus.valueOf(entry.ownershipStatus)
    }

    override suspend fun get(reference: AccountReference): AccountRegistryEntry? {
        val masked = reference.maskedNumber?.trim().orEmpty()
        if (masked.isEmpty()) return null
        return dao.get(reference.bank.id, masked)?.let(RegistryMapper::toAccountEntry)
    }

    override suspend fun listAll(): List<AccountRegistryEntry> =
        dao.listAll().map(RegistryMapper::toAccountEntry)
}
