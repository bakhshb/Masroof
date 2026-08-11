package com.baraa.masroof.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.baraa.masroof.data.room.entity.FinancialTransactionEntity
import com.baraa.masroof.data.room.entity.FinancialTransactionRawSmsLinkEntity

@Dao
interface FinancialTransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionIfAbsent(entity: FinancialTransactionEntity): Long

    @Query(
        """
        UPDATE financial_transaction SET
          type = :type,
          amountDecimal = :amountDecimal,
          amountCurrency = :amountCurrency,
          occurredAtEpochMillis = :occurredAtEpochMillis,
          sourceContainerId = :sourceContainerId,
          destinationContainerId = :destinationContainerId,
          merchant = :merchant,
          counterparty = :counterparty,
          categoryId = :categoryId
        WHERE id = :id
        """,
    )
    suspend fun updateTransaction(
        id: String,
        type: String,
        amountDecimal: String,
        amountCurrency: String,
        occurredAtEpochMillis: Long,
        sourceContainerId: String?,
        destinationContainerId: String?,
        merchant: String?,
        counterparty: String?,
        categoryId: String?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinkIfAbsent(entity: FinancialTransactionRawSmsLinkEntity): Long

    @Query("SELECT * FROM financial_transaction WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transaction ORDER BY occurredAtEpochMillis, id")
    suspend fun listAll(): List<FinancialTransactionEntity>

    @Query(
        """
        SELECT * FROM financial_transaction_raw_sms_link
        WHERE rawSmsId = :rawSmsId
        LIMIT 1
        """,
    )
    suspend fun findLinkByRawSmsId(rawSmsId: String): FinancialTransactionRawSmsLinkEntity?

    @Query(
        """
        SELECT rawSmsId FROM financial_transaction_raw_sms_link
        WHERE transactionId = :transactionId
        ORDER BY rawSmsId
        """,
    )
    suspend fun listRawSmsIdsForTransaction(transactionId: String): List<String>

    @Query("SELECT COUNT(*) FROM financial_transaction")
    suspend fun count(): Int

    @Transaction
    suspend fun saveAtomic(
        entity: FinancialTransactionEntity,
        links: List<FinancialTransactionRawSmsLinkEntity>,
    ): SaveAtomicOutcome {
        for (link in links) {
            val existing = findLinkByRawSmsId(link.rawSmsId)
            if (existing != null && existing.transactionId != entity.id) {
                return SaveAtomicOutcome.Conflict(link.rawSmsId, existing.transactionId)
            }
        }

        val inserted = insertTransactionIfAbsent(entity)
        if (inserted == -1L) {
            // Same id already present — refresh columns (idempotent re-save).
            updateTransaction(
                id = entity.id,
                type = entity.type,
                amountDecimal = entity.amountDecimal,
                amountCurrency = entity.amountCurrency,
                occurredAtEpochMillis = entity.occurredAtEpochMillis,
                sourceContainerId = entity.sourceContainerId,
                destinationContainerId = entity.destinationContainerId,
                merchant = entity.merchant,
                counterparty = entity.counterparty,
                categoryId = entity.categoryId,
            )
        }

        var allLinksAlreadyPresent = inserted == -1L
        for (link in links) {
            val linkInsert = insertLinkIfAbsent(link)
            if (linkInsert != -1L) {
                allLinksAlreadyPresent = false
            }
        }

        return if (allLinksAlreadyPresent && inserted == -1L) {
            SaveAtomicOutcome.AlreadyExists
        } else {
            SaveAtomicOutcome.Saved
        }
    }

    sealed interface SaveAtomicOutcome {
        data object Saved : SaveAtomicOutcome

        data object AlreadyExists : SaveAtomicOutcome

        data class Conflict(
            val rawSmsId: String,
            val existingTransactionId: String,
        ) : SaveAtomicOutcome
    }
}
