package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao interface AccountIdentifierDao {
    @Query("SELECT * FROM account_identifiers WHERE accountId = :accountId ORDER BY identifierType, id")
    fun observeByAccount(accountId: Long): Flow<List<AccountIdentifierEntity>>

    @Query("SELECT * FROM account_identifiers ORDER BY accountId, identifierType")
    fun observeAll(): Flow<List<AccountIdentifierEntity>>

    @Query("SELECT * FROM account_identifiers WHERE accountId = :accountId")
    suspend fun getByAccount(accountId: Long): List<AccountIdentifierEntity>

    @Query("SELECT * FROM account_identifiers WHERE normalizedValue = :value AND isActive = 1")
    suspend fun findByValue(value: String): AccountIdentifierEntity?

    @Query("SELECT * FROM account_identifiers WHERE identifierType = :type AND normalizedValue = :value AND isActive = 1")
    suspend fun findByTypeAndValue(type: AccountIdentifierType, value: String): AccountIdentifierEntity?

    @Query("SELECT * FROM account_identifiers WHERE identifierType = :type AND isActive = 1")
    suspend fun getByType(type: AccountIdentifierType): List<AccountIdentifierEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(identifier: AccountIdentifierEntity): Long

    @Update
    suspend fun update(identifier: AccountIdentifierEntity): Int

    @Delete
    suspend fun delete(identifier: AccountIdentifierEntity): Int
}
