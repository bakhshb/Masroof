package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantMemoryDao {

    @Query("SELECT * FROM merchant_memory")
    fun observeAll(): Flow<List<MerchantMemoryEntity>>

    @Query("SELECT * FROM merchant_memory")
    suspend fun getAll(): List<MerchantMemoryEntity>

    @Query("SELECT * FROM merchant_memory WHERE normalizedKey = :key")
    suspend fun getByKey(key: String): MerchantMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MerchantMemoryEntity)

    @Update
    suspend fun update(memory: MerchantMemoryEntity): Int

    @Query("UPDATE merchant_memory SET enabled = :enabled WHERE normalizedKey = :key")
    suspend fun setEnabled(key: String, enabled: Boolean): Int

    @Query("DELETE FROM merchant_memory WHERE normalizedKey = :key")
    suspend fun delete(key: String): Int
}
