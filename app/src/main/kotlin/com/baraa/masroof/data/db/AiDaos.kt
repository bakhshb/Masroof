package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCacheDao {

    @Query("SELECT * FROM `ai_cache` WHERE `normalizedMerchantKey` = :key LIMIT 1")
    suspend fun getByKey(key: String): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiCacheEntity)

    @Query("UPDATE `ai_cache` SET `userAccepted` = 1 WHERE `normalizedMerchantKey` = :key")
    suspend fun markAccepted(key: String)

    @Query("UPDATE `ai_cache` SET `userRejected` = 1 WHERE `lastUsedAt` = (SELECT MAX(`lastUsedAt`) FROM `ai_cache` WHERE `normalizedMerchantKey` = :key) AND `normalizedMerchantKey` = :key")
    suspend fun markRejected(key: String)

    @Query("UPDATE `ai_cache` SET `lastUsedAt` = :now, `usageCount` = `usageCount` + 1 WHERE `normalizedMerchantKey` = :key")
    suspend fun touch(key: String, now: Long)

    @Query("DELETE FROM `ai_cache`")
    suspend fun deleteAll()

    @Query("DELETE FROM `ai_cache` WHERE `categoryId` = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)
}

@Dao
interface AiSettingsDao {

    @Query("SELECT * FROM `ai_settings` WHERE `id` = 1 LIMIT 1")
    suspend fun get(): AiSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiSettingsEntity)
}

@Dao
interface AiSuggestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiSuggestionEntity): Long

    @Update
    suspend fun update(entity: AiSuggestionEntity): Int

    @Query("SELECT * FROM `ai_suggestions` WHERE `id` = :id LIMIT 1")
    suspend fun getById(id: Long): AiSuggestionEntity?

    @Query("SELECT * FROM `ai_suggestions` WHERE `transactionId` = :transactionId ORDER BY `createdAt` DESC")
    suspend fun getByTransactionId(transactionId: Long): List<AiSuggestionEntity>

    /** All suggestions, newest first. */
    @Query("SELECT * FROM `ai_suggestions` ORDER BY `createdAt` DESC")
    fun observeAll(): Flow<List<AiSuggestionEntity>>

    @Query("SELECT * FROM `ai_suggestions` WHERE `status` = :status ORDER BY `createdAt` DESC")
    fun observeByStatus(status: String): Flow<List<AiSuggestionEntity>>

    /** Pending suggestions only, newest first — the review queue. */
    @Query("SELECT * FROM `ai_suggestions` WHERE `status` = 'PENDING' ORDER BY `createdAt` DESC")
    fun observePending(): Flow<List<AiSuggestionEntity>>

    @Query("UPDATE `ai_suggestions` SET `status` = :status, `updatedAt` = :now WHERE `id` = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long): Int

    @Query("DELETE FROM `ai_suggestions`")
    suspend fun deleteAll()
}