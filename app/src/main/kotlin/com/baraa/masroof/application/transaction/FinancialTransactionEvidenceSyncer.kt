package com.baraa.masroof.application.transaction

import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Backfills durable transaction fields from the latest linked ParsedEvent evidence.
 */
object FinancialTransactionEvidenceSyncer {
    suspend fun syncMerchants(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        repository: FinancialTransactionRepository,
    ): Int {
        if (transactions.isEmpty() || parsedRecords.isEmpty()) return 0
        val parsedByEventId = parsedRecords.associateBy { it.event.id }
        var updated = 0
        for (tx in transactions) {
            if (!tx.merchant.isNullOrBlank()) continue
            val merchant = tx.linkedParsedEventIds
                .asSequence()
                .mapNotNull { parsedByEventId[it]?.event?.merchant }
                .firstOrNull { it.isNotBlank() }
                ?: continue
            if (repository.update(tx.copy(merchant = merchant))) {
                updated++
            }
        }
        return updated
    }
}
