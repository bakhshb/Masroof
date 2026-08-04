package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao interface AccountLinkRuleDao {
    @Query("SELECT * FROM account_link_rules ORDER BY active DESC, lastConfirmedAt DESC") fun observeAll(): Flow<List<AccountLinkRuleEntity>>
    @Query("SELECT * FROM account_link_rules WHERE signature = :signature LIMIT 1") suspend fun bySignature(signature: String): AccountLinkRuleEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(rule: AccountLinkRuleEntity): Long
    @Update suspend fun update(rule: AccountLinkRuleEntity): Int
    @Delete suspend fun delete(rule: AccountLinkRuleEntity): Int
}
