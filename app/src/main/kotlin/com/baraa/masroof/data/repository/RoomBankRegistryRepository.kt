package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.BankRegistryDao
import com.baraa.masroof.data.room.entity.BankRegistryEntity
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankRegistryEntry
import com.baraa.masroof.domain.ownership.RegistryIdentity
import com.baraa.masroof.domain.repository.BankRegistryRepository

class RoomBankRegistryRepository(
    private val dao: BankRegistryDao,
) : BankRegistryRepository {
    override suspend fun ensureKnown(bank: Bank) {
        if (!RegistryIdentity.isKnownBank(bank)) return
        dao.insertIfAbsent(BankRegistryEntity(bankId = bank.id))
    }

    override suspend fun listAll(): List<BankRegistryEntry> =
        dao.listAll().map { entity ->
            BankRegistryEntry(
                bank = Bank(entity.bankId),
                displayName = entity.displayName,
            )
        }
}
