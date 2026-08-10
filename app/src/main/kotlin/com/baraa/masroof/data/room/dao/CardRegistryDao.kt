package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.data.room.entity.CardRegistryEntity

@Dao
interface CardRegistryDao {
    /** Atomic create-if-absent. Returns -1 when the composite key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CardRegistryEntity): Long

    /**
     * Observation metadata only — never touches [CardRegistryEntity.ownershipStatus].
     * Preserves existing [firstSeenRawSmsId] when already set.
     */
    @Query(
        """
        UPDATE card_registry
        SET lastSeenRawSmsId = :rawSmsId,
            firstSeenRawSmsId = COALESCE(firstSeenRawSmsId, :rawSmsId)
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun touchObservation(bankId: String, last4: String, rawSmsId: String): Int

    @Query(
        """
        UPDATE card_registry
        SET ownershipStatus = :ownershipStatus
        WHERE bankId = :bankId AND last4 = :last4
        """,
    )
    suspend fun updateOwnership(
        bankId: String,
        last4: String,
        ownershipStatus: String,
    ): Int

    /**
     * Atomically ensure the row exists and set ownership.
     * On conflict, only [ownershipStatus] is updated — observation metadata is kept.
     */
    @Query(
        """
        INSERT INTO card_registry
          (bankId, last4, ownershipStatus, firstSeenRawSmsId, lastSeenRawSmsId)
        VALUES (:bankId, :last4, :ownershipStatus, NULL, NULL)
        ON CONFLICT(bankId, last4) DO UPDATE SET
          ownershipStatus = :ownershipStatus
        """,
    )
    suspend fun upsertOwnership(
        bankId: String,
        last4: String,
        ownershipStatus: String,
    )

    @Query(
        """
        SELECT * FROM card_registry
        WHERE bankId = :bankId AND last4 = :last4
        LIMIT 1
        """,
    )
    suspend fun get(bankId: String, last4: String): CardRegistryEntity?

    @Query("SELECT * FROM card_registry ORDER BY bankId, last4")
    suspend fun listAll(): List<CardRegistryEntity>
}
