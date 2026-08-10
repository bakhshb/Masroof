package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.baraa.masroof.data.room.entity.ParsedEventEntity

@Dao
interface ParsedEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ParsedEventEntity)

    @Query("SELECT * FROM parsed_event WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ParsedEventEntity?

    @Query("SELECT * FROM parsed_event WHERE rawSmsId = :rawSmsId LIMIT 1")
    suspend fun findByRawSmsId(rawSmsId: String): ParsedEventEntity?

    @Query("DELETE FROM parsed_event WHERE rawSmsId = :rawSmsId")
    suspend fun deleteByRawSmsId(rawSmsId: String): Int

    @Query("SELECT COUNT(*) FROM parsed_event")
    suspend fun count(): Int

    /**
     * Replace the current parse result for a RawSms (same or new event id).
     * Deletes any existing row for [entity.rawSmsId], then inserts [entity].
     */
    @Transaction
    suspend fun replaceForRawSms(entity: ParsedEventEntity) {
        deleteByRawSmsId(entity.rawSmsId)
        upsert(entity)
    }
}
