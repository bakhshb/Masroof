package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.data.room.entity.RawSmsEntity

@Dao
interface RawSmsDao {
    /**
     * Atomic insert gated by unique constraints (id, deviceMessageId, dedupeKey).
     * Returns the row id, or **-1** when ignored due to conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: RawSmsEntity): Long

    @Query("SELECT * FROM raw_sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RawSmsEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM raw_sms WHERE id = :id)")
    suspend fun existsById(id: String): Boolean

    @Query("SELECT * FROM raw_sms WHERE deviceMessageId = :deviceMessageId LIMIT 1")
    suspend fun findByDeviceMessageId(deviceMessageId: String): RawSmsEntity?

    @Query("SELECT * FROM raw_sms WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): RawSmsEntity?

    @Query("SELECT COUNT(*) FROM raw_sms")
    suspend fun count(): Int
}
