package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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