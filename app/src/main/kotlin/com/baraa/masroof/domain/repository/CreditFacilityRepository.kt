package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.CreditFacilityEntry

interface CreditFacilityRepository {
    suspend fun listAll(): List<CreditFacilityEntry>

    suspend fun listByBank(bankId: String): List<CreditFacilityEntry>
}
