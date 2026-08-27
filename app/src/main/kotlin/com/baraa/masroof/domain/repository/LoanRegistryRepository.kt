package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.LoanRegistryEntry

interface LoanRegistryRepository {
    suspend fun listAll(): List<LoanRegistryEntry>
}
