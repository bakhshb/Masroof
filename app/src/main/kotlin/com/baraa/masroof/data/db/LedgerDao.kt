package com.baraa.masroof.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.baraa.masroof.ledger.JournalPostingStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournal(journal: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPostings(postings: List<LedgerPostingEntity>)

    @Update suspend fun updateJournal(journal: JournalEntryEntity): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteJournal(id: Long): Int
    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournal(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM ledger_postings WHERE journalEntryId = :journalId ORDER BY id")
    suspend fun getPostings(journalId: Long): List<LedgerPostingEntity>

    @Transaction
    suspend fun getWithPostings(id: Long): JournalWithPostings? {
        val journal = getJournal(id) ?: return null
        return JournalWithPostings(journal, getPostings(id))
    }

    @Query("SELECT * FROM journal_entries WHERE sourceTransactionId = :transactionId ORDER BY id DESC")
    suspend fun getForTransaction(transactionId: Long): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE postingStatus = 'POSTED' AND (effectiveDate < :date OR (effectiveDate = :date AND effectiveTime <= :time))")
    suspend fun postedAsOf(date: LocalDate, time: LocalTime): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE postingStatus IN ('DRAFT', 'NEEDS_REVIEW') ORDER BY effectiveDate DESC, effectiveTime DESC")
    fun observeReviewable(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE postingStatus = 'POSTED' ORDER BY effectiveDate DESC, effectiveTime DESC")
    fun observePosted(): Flow<List<JournalEntryEntity>>
}
