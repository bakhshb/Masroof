package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.data.room.entity.CreditFacilityEntity

@Dao
interface CreditFacilityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CreditFacilityEntity): Long

    @Query("SELECT * FROM credit_facility WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CreditFacilityEntity?

    @Query("SELECT * FROM credit_facility WHERE bankId = :bankId ORDER BY primaryLast4")
    suspend fun listByBank(bankId: String): List<CreditFacilityEntity>

    @Query("SELECT * FROM credit_facility ORDER BY bankId, primaryLast4")
    suspend fun listAll(): List<CreditFacilityEntity>

    @Query(
        """
        UPDATE credit_facility
        SET displayName = :displayName
        WHERE id = :id
        """,
    )
    suspend fun updateDisplayName(id: String, displayName: String?): Int
}
