package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialAccountDao {

    @Query("SELECT * FROM financial_accounts ORDER BY displayName, id")
    fun observeAll(): Flow<List<FinancialAccountEntity>>

    @Query("SELECT * FROM financial_accounts WHERE isActive = 1 ORDER BY displayName, id")
    suspend fun getActive(): List<FinancialAccountEntity>

    @Query("SELECT * FROM financial_accounts WHERE isOwnedByUser = 1 AND isActive = 1")
    suspend fun getOwnedActive(): List<FinancialAccountEntity>

    @Query("SELECT * FROM financial_accounts WHERE id = :id")
    suspend fun getById(id: Long): FinancialAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: FinancialAccountEntity): Long

    @Update
    suspend fun update(account: FinancialAccountEntity): Int

    @Delete
    suspend fun delete(account: FinancialAccountEntity): Int
}
