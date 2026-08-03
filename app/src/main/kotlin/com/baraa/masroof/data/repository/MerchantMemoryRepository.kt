package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.MerchantMemory
import com.baraa.masroof.data.db.MerchantMemoryDao
import com.baraa.masroof.data.db.MerchantMemoryEntity
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MerchantMemoryRepository {
    fun observeAll(): Flow<List<MerchantMemory>>
    suspend fun getAll(): List<MerchantMemory>
    suspend fun getByKey(key: String): MerchantMemory?
    suspend fun remember(
        rawMerchant: String?,
        displayName: String,
        categoryId: Long?,
        treatment: FinancialTreatment?,
    )
    suspend fun setEnabled(key: String, enabled: Boolean)
    suspend fun delete(key: String)
    suspend fun merge(fromKey: String, intoKey: String)
}

class RoomMerchantMemoryRepository(
    private val dao: MerchantMemoryDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : MerchantMemoryRepository {

    override fun observeAll(): Flow<List<MerchantMemory>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<MerchantMemory> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getByKey(key: String): MerchantMemory? =
        dao.getByKey(key)?.toDomain()

    override suspend fun remember(
        rawMerchant: String?,
        displayName: String,
        categoryId: Long?,
        treatment: FinancialTreatment?,
    ) {
        val key = MerchantNormalizer.normalize(rawMerchant)
        if (key.isBlank()) return
        val existing = dao.getByKey(key)
        val updated = if (existing == null) {
            MerchantMemoryEntity(
                normalizedKey = key,
                displayName = displayName,
                preferredCategoryId = categoryId,
                preferredFinancialTreatment = treatment,
                confirmationCount = 1,
                lastConfirmedAt = now(),
                enabled = true,
            )
        } else {
            existing.copy(
                displayName = displayName,
                preferredCategoryId = categoryId,
                preferredFinancialTreatment = treatment,
                confirmationCount = existing.confirmationCount + 1,
                lastConfirmedAt = now(),
            )
        }
        dao.upsert(updated)
    }

    override suspend fun setEnabled(key: String, enabled: Boolean) {
        dao.setEnabled(key, enabled)
    }

    override suspend fun delete(key: String) {
        dao.delete(key)
    }

    override suspend fun merge(fromKey: String, intoKey: String) {
        if (fromKey == intoKey) return
        val from = dao.getByKey(fromKey) ?: return
        val into = dao.getByKey(intoKey) ?: return
        dao.upsert(
            into.copy(
                displayName = into.displayName,
                preferredCategoryId = into.preferredCategoryId,
                preferredFinancialTreatment = into.preferredFinancialTreatment,
                confirmationCount = into.confirmationCount + from.confirmationCount,
                lastConfirmedAt = maxOf(into.lastConfirmedAt, from.lastConfirmedAt),
            )
        )
        dao.delete(fromKey)
    }
}

internal fun MerchantMemoryEntity.toDomain(): MerchantMemory = MerchantMemory(
    normalizedKey = normalizedKey,
    displayName = displayName,
    preferredCategoryId = preferredCategoryId,
    preferredFinancialTreatment = preferredFinancialTreatment,
    confirmationCount = confirmationCount,
    lastConfirmedAt = lastConfirmedAt,
    enabled = enabled,
)
