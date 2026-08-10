package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.data.room.entity.AccountRegistryEntity

@Dao
interface AccountRegistryDao {
    /** Atomic create-if-absent. Returns -1 when the composite key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: AccountRegistryEntity): Long

    /**
     * Observation metadata only — never touches [AccountRegistryEntity.ownershipStatus].
     * Preserves existing [firstSeenRawSmsId] when already set.
     */
    @Query(
        """
        UPDATE account_registry
        SET lastSeenRawSmsId = :rawSmsId,
            firstSeenRawSmsId = COALESCE(firstSeenRawSmsId, :rawSmsId)
        WHERE bankId = :bankId AND maskedNumber = :maskedNumber
        """,
    )
    suspend fun touchObservation(bankId: String, maskedNumber: String, rawSmsId: String): Int

    @Query(
        """
        UPDATE account_registry
        SET ownershipStatus = :ownershipStatus
        WHERE bankId = :bankId AND maskedNumber = :maskedNumber
        """,
    )
    suspend fun updateOwnership(
        bankId: String,
        maskedNumber: String,
        ownershipStatus: String,
    ): Int

    /**
     * Atomically ensure the row exists and set ownership.
     * On conflict, only [ownershipStatus] is updated — observation metadata is kept.
     */
    @Query(
        """
        INSERT INTO account_registry
          (bankId, maskedNumber, ownershipStatus, firstSeenRawSmsId, lastSeenRawSmsId)
        VALUES (:bankId, :maskedNumber, :ownershipStatus, NULL, NULL)
        ON CONFLICT(bankId, maskedNumber) DO UPDATE SET
          ownershipStatus = :ownershipStatus
        """,
    )
    suspend fun upsertOwnership(
        bankId: String,
        maskedNumber: String,
        ownershipStatus: String,
    )

    @Query(
        """
        SELECT * FROM account_registry
        WHERE bankId = :bankId AND maskedNumber = :maskedNumber
        LIMIT 1
        """,
    )
    suspend fun get(bankId: String, maskedNumber: String): AccountRegistryEntity?

    @Query("SELECT * FROM account_registry ORDER BY bankId, maskedNumber")
    suspend fun listAll(): List<AccountRegistryEntity>
}
