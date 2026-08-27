package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.baraa.masroof.data.room.entity.LoanRegistryEntity

@Dao
interface LoanRegistryDao {
    @Query("SELECT * FROM loan_registry ORDER BY bankId, id")
    suspend fun listAll(): List<LoanRegistryEntity>
}
