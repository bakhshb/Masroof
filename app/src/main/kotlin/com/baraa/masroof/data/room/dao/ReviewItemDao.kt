package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.baraa.masroof.data.room.entity.ReviewItemEntity

@Dao
interface ReviewItemDao {
    @Query("SELECT * FROM review_item WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReviewItemEntity?

    @Query("SELECT * FROM review_item WHERE rawSmsId = :rawSmsId LIMIT 1")
    suspend fun findByRawSmsId(rawSmsId: String): ReviewItemEntity?

    @Query("SELECT * FROM review_item WHERE status = :status ORDER BY createdAtEpochMillis, id")
    suspend fun listByStatus(status: String): List<ReviewItemEntity>

    @Query("SELECT * FROM review_item ORDER BY createdAtEpochMillis, id")
    suspend fun listAll(): List<ReviewItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: ReviewItemEntity): Long

    @Query(
        """
        UPDATE review_item SET
          kind = :kind,
          status = :status,
          reasons = :reasons,
          updatedAtEpochMillis = :updatedAtEpochMillis,
          resolvedAtEpochMillis = :resolvedAtEpochMillis,
          resolutionKind = :resolutionKind,
          resolvedTransactionId = :resolvedTransactionId
        WHERE id = :id
        """,
    )
    suspend fun updateRow(
        id: String,
        kind: String,
        status: String,
        reasons: String,
        updatedAtEpochMillis: Long,
        resolvedAtEpochMillis: Long?,
        resolutionKind: String?,
        resolvedTransactionId: String?,
    ): Int

    @Transaction
    suspend fun upsertRequiredAtomic(entity: ReviewItemEntity): ReviewItemEntity {
        val inserted = insertIfAbsent(entity)
        if (inserted != -1L) {
            return entity
        }
        val existing = findByRawSmsId(entity.rawSmsId)
            ?: getById(entity.id)
            ?: return entity
        if (existing.status == entity.status &&
            existing.kind == entity.kind &&
            existing.reasons == entity.reasons &&
            existing.resolvedAtEpochMillis == null &&
            existing.resolutionKind == null &&
            existing.resolvedTransactionId == null
        ) {
            return existing
        }
        updateRow(
            id = existing.id,
            kind = entity.kind,
            status = entity.status,
            reasons = entity.reasons,
            updatedAtEpochMillis = entity.updatedAtEpochMillis,
            resolvedAtEpochMillis = null,
            resolutionKind = null,
            resolvedTransactionId = null,
        )
        return existing.copy(
            kind = entity.kind,
            status = entity.status,
            reasons = entity.reasons,
            updatedAtEpochMillis = entity.updatedAtEpochMillis,
            resolvedAtEpochMillis = null,
            resolutionKind = null,
            resolvedTransactionId = null,
        )
    }

    @Transaction
    suspend fun markResolvedAtomic(
        id: String,
        status: String,
        resolutionKind: String,
        resolvedAtEpochMillis: Long,
        resolvedTransactionId: String?,
        updatedAtEpochMillis: Long,
    ): ReviewItemEntity? {
        val existing = getById(id) ?: return null
        updateRow(
            id = id,
            kind = existing.kind,
            status = status,
            reasons = existing.reasons,
            updatedAtEpochMillis = updatedAtEpochMillis,
            resolvedAtEpochMillis = resolvedAtEpochMillis,
            resolutionKind = resolutionKind,
            resolvedTransactionId = resolvedTransactionId,
        )
        return existing.copy(
            status = status,
            updatedAtEpochMillis = updatedAtEpochMillis,
            resolvedAtEpochMillis = resolvedAtEpochMillis,
            resolutionKind = resolutionKind,
            resolvedTransactionId = resolvedTransactionId,
        )
    }
}
