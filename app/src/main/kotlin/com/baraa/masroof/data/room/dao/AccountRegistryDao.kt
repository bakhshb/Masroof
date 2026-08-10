package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baraa.masroof.data.room.entity.AccountRegistryEntity

@Dao
interface AccountRegistryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AccountRegistryEntity)

    @Query(
        """
        UPDATE account_registry
        SET lastSeenRawSmsId = :rawSmsId,
            evidenceCount = evidenceCount + 1
        WHERE bankId = :bankId AND maskedNumber = :maskedNumber
        """,
    )
    suspend fun touchEvidence(bankId: String, maskedNumber: String, rawSmsId: String): Int

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
