package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.FinancialTransactionDao
import com.baraa.masroof.data.room.dao.ParsedEventDao
import com.baraa.masroof.data.room.entity.FinancialTransactionRawSmsLinkEntity
import com.baraa.masroof.data.room.mapper.FinancialTransactionMapper
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult

class RoomFinancialTransactionRepository(
    private val dao: FinancialTransactionDao,
    private val parsedEventDao: ParsedEventDao,
) : FinancialTransactionRepository {
    override suspend fun save(
        transaction: FinancialTransaction,
        rawSmsIds: Collection<String>,
    ): FinancialTransactionSaveResult {
        val ids = rawSmsIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        require(ids.isNotEmpty()) { "rawSmsIds required" }

        val entity = FinancialTransactionMapper.toEntity(transaction)
        val links = ids.map { FinancialTransactionRawSmsLinkEntity(it, transaction.id) }

        return when (val outcome = dao.saveAtomic(entity, links)) {
            FinancialTransactionDao.SaveAtomicOutcome.Saved ->
                FinancialTransactionSaveResult.Saved

            FinancialTransactionDao.SaveAtomicOutcome.AlreadyExists ->
                FinancialTransactionSaveResult.AlreadyExists

            is FinancialTransactionDao.SaveAtomicOutcome.Conflict ->
                FinancialTransactionSaveResult.Conflict(
                    rawSmsId = outcome.rawSmsId,
                    existingTransactionId = outcome.existingTransactionId,
                )
        }
    }

    override suspend fun getById(id: String): FinancialTransaction? {
        val entity = dao.getById(id) ?: return null
        return reconstruct(entity)
    }

    override suspend fun findByRawSmsId(rawSmsId: String): FinancialTransaction? {
        val link = dao.findLinkByRawSmsId(rawSmsId) ?: return null
        return getById(link.transactionId)
    }

    override suspend fun listAll(): List<FinancialTransaction> =
        dao.listAll().map { reconstruct(it) }

    override suspend fun isRawSmsLinked(rawSmsId: String): Boolean =
        dao.findLinkByRawSmsId(rawSmsId) != null

    private suspend fun reconstruct(
        entity: com.baraa.masroof.data.room.entity.FinancialTransactionEntity,
    ): FinancialTransaction {
        val rawSmsIds = dao.listRawSmsIdsForTransaction(entity.id)
        val linkedEventIds = rawSmsIds.mapNotNull { rawId ->
            parsedEventDao.findByRawSmsId(rawId)?.id
        }.sorted()
        return FinancialTransactionMapper.toDomain(entity, linkedEventIds)
    }
}
