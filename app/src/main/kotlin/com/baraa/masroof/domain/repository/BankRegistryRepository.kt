package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankRegistryEntry

interface BankRegistryRepository {
    suspend fun ensureKnown(bank: Bank)

    suspend fun listAll(): List<BankRegistryEntry>
}
