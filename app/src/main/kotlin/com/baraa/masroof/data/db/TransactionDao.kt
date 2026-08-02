package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for the local `transactions` table.
 *
 * All writes use [OnConflictStrategy.IGNORE] on the unique `uniqueFingerprint`
 * index so that re-importing the same SMS does not crash and does not create
 * duplicates. The returned id is `-1` when the row was ignored.
 */
@Dao
interface TransactionDao {

    // -- Insert ---------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    // -- Read -----------------------------------------------------------------

    @Query("SELECT * FROM transactions ORDER BY smsTimestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY smsTimestamp DESC")
    suspend fun getAllNewestFirst(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    // -- Update / Delete ------------------------------------------------------

    @Update
    suspend fun update(transaction: TransactionEntity): Int

    @Delete
    suspend fun delete(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // -- Aggregates / dedupe probes ------------------------------------------

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE uniqueFingerprint = :fingerprint LIMIT 1)")
    suspend fun existsByFingerprint(fingerprint: String): Boolean

    /**
     * Find transactions whose computed similarity key matches [key]. Used by
     * the import service to detect near-duplicate transactions that arrived
     * in separate SMS messages. Sorted by `smsTimestamp DESC` so the most
     * recent candidate is at index 0 — the duplicate-window check then
     * compares against the incoming message's timestamp.
     */
    @Query("SELECT * FROM transactions WHERE transactionSimilarityKey = :key ORDER BY smsTimestamp DESC")
    suspend fun findBySimilarityKey(key: String): List<TransactionEntity>
}
