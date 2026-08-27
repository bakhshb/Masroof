package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.LoanRegistryDao
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanRegistryEntry
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.repository.LoanRegistryRepository

class RoomLoanRegistryRepository(
    private val dao: LoanRegistryDao,
) : LoanRegistryRepository {
    override suspend fun listAll(): List<LoanRegistryEntry> =
        dao.listAll().map { entity ->
            LoanRegistryEntry(
                id = entity.id,
                bank = Bank(entity.bankId),
                loanType = LoanType.valueOf(entity.loanType),
                ownership = OwnershipStatus.valueOf(entity.ownershipStatus),
                displayName = entity.displayName,
                firstSeenRawSmsId = entity.firstSeenRawSmsId,
                lastSeenRawSmsId = entity.lastSeenRawSmsId,
            )
        }
}
