package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baraa.masroof.data.room.entity.CommitmentEntity

@Dao
interface CommitmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CommitmentEntity)

    @Update
    suspend fun update(entity: CommitmentEntity)

    @Query("DELETE FROM commitment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE commitment SET active = :active, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAtEpochMillis: Long)

    @Query("SELECT * FROM commitment WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CommitmentEntity?

    @Query("SELECT * FROM commitment WHERE sourceTransactionId = :sourceTransactionId LIMIT 1")
    suspend fun getBySourceTransactionId(sourceTransactionId: String): CommitmentEntity?

    @Query("SELECT * FROM commitment ORDER BY name COLLATE NOCASE, id")
    suspend fun listAll(): List<CommitmentEntity>

    @Query("SELECT * FROM commitment WHERE active = 1 ORDER BY name COLLATE NOCASE, id")
    suspend fun listActive(): List<CommitmentEntity>
}
