package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FinancialSetupDao {

    @Query("SELECT * FROM `financial_setup` WHERE `id` = 1 LIMIT 1")
    suspend fun get(): FinancialSetupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FinancialSetupEntity)
}
