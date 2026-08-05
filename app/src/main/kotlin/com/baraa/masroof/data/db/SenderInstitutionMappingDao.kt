package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderInstitutionMappingDao {
    @Query("SELECT * FROM sender_institution_mapping ORDER BY isActive DESC, lastConfirmedAt DESC")
    fun observeAll(): Flow<List<SenderInstitutionMappingEntity>>

    @Query("SELECT * FROM sender_institution_mapping WHERE isActive = 1")
    suspend fun getActive(): List<SenderInstitutionMappingEntity>

    @Query("SELECT * FROM sender_institution_mapping WHERE senderKey = :key LIMIT 1")
    suspend fun findByKey(key: String): SenderInstitutionMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: SenderInstitutionMappingEntity): Long

    @Update
    suspend fun update(mapping: SenderInstitutionMappingEntity): Int

    @Query("UPDATE sender_institution_mapping SET isActive = :active, lastConfirmedAt = :lastConfirmedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, lastConfirmedAt: Long): Int

    @Query("DELETE FROM sender_institution_mapping WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
