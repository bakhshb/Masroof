package com.baraa.masroof.ledger

import androidx.room.withTransaction
import com.baraa.masroof.data.db.MasroofDatabase

/**
 * User-initiated wipe of imported ledger data for experimentation.
 *
 * Deletes transactions, journals, and postings so SMS can be re-imported
 * cleanly. **Preserves** owned accounts, typed identifiers, opening
 * balances, categories, merchant memory, financial setup, sender profiles,
 * and message pattern definitions.
 *
 * Never runs automatically — only from an explicit UI confirmation.
 */
class ImportResetService(
    private val database: MasroofDatabase,
) {
    data class Result(
        val deletedTransactions: Int,
        val deletedJournals: Int,
    )

    suspend fun clearImportedLedger(): Result = database.withTransaction {
        val txCount = database.transactionDao().count()
        val journalCount = database.journalDao().countJournals()
        // Postings first (safe if CASCADE missing), then journals, then rows.
        database.journalDao().deleteAllPostings()
        database.journalDao().deleteAllJournals()
        runCatching { database.aiSuggestionDao().deleteAll() }
        database.transactionDao().deleteAll()
        Result(
            deletedTransactions = txCount,
            deletedJournals = journalCount,
        )
    }
}
