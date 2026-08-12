package com.baraa.masroof.domain.repository

import com.baraa.masroof.domain.model.FinancialTransaction
import java.time.Instant

/**
 * Persistence for reconciled [FinancialTransaction]s linked by stable RawSms ids.
 */
interface FinancialTransactionRepository {
    /**
     * Atomically persist [transaction] and its source [rawSmsIds].
     *
     * Idempotent when the same deterministic transaction already owns those links.
     * Conflicts when a rawSmsId is already linked to a different transaction.
     */
    suspend fun save(
        transaction: FinancialTransaction,
        rawSmsIds: Collection<String>,
    ): FinancialTransactionSaveResult

    suspend fun getById(id: String): FinancialTransaction?

    suspend fun findByRawSmsId(rawSmsId: String): FinancialTransaction?

    suspend fun listAll(): List<FinancialTransaction>

    /**
     * Transactions with occurredAt in `[startInclusive, endExclusive)`, newest first.
     */
    suspend fun listOccurredBetween(
        startInclusive: Instant,
        endExclusive: Instant,
    ): List<FinancialTransaction>

    suspend fun isRawSmsLinked(rawSmsId: String): Boolean

    suspend fun listRawSmsIds(transactionId: String): List<String>

    suspend fun update(transaction: FinancialTransaction): Boolean

    /**
     * Removes a transaction when [rawSmsId] is its only linked SMS evidence.
     * Used after reparse when a message is reclassified as non-financial (e.g. OTP).
     */
    suspend fun deleteIfExclusiveRawSmsLink(rawSmsId: String): Boolean
}

sealed interface FinancialTransactionSaveResult {
    data object Saved : FinancialTransactionSaveResult

    data object AlreadyExists : FinancialTransactionSaveResult

    data class Conflict(
        val rawSmsId: String,
        val existingTransactionId: String,
    ) : FinancialTransactionSaveResult
}
