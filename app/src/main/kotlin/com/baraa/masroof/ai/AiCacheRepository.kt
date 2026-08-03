package com.baraa.masroof.ai

import com.baraa.masroof.data.db.AiCacheDao
import com.baraa.masroof.data.db.AiCacheEntity
import com.baraa.masroof.transaction.MerchantNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for the AI categorization cache. Only sanitized fields are
 * stored. Raw prompts / responses / API keys are NEVER persisted.
 */
class AiCacheRepository(private val dao: AiCacheDao) {

    /**
     * Look up a cached suggestion for [normalizedMerchant]. Returns null
     * when:
     *  - no row exists
     *  - the row is marked `userRejected`
     *  - the row is for an older prompt version
     *
     * The [categoryEnabled] callback should return false when the
     * suggestion's category was deleted or disabled; we treat that as a
     * cache miss.
     */
    suspend fun lookup(
        normalizedMerchant: String,
        categoryEnabled: (Long) -> Boolean,
    ): AiCacheEntity? = withContext(Dispatchers.IO) {
        val key = MerchantNormalizer.normalize(normalizedMerchant)
        val row = dao.getByKey(key) ?: return@withContext null
        if (row.userRejected) return@withContext null
        if (row.promptVersion != AiPromptBuilder.PROMPT_VERSION) return@withContext null
        if (!categoryEnabled(row.categoryId)) return@withContext null
        // Bump lastUsedAt + usageCount.
        dao.touch(key, now = System.currentTimeMillis())
        row.copy(
            lastUsedAt = System.currentTimeMillis(),
            usageCount = row.usageCount + 1,
        )
    }

    /** Insert or replace a cache entry from an AI result. */
    suspend fun store(result: AiCategorizationResult, normalizedMerchant: String) = withContext(Dispatchers.IO) {
        val key = MerchantNormalizer.normalize(normalizedMerchant)
        val now = System.currentTimeMillis()
        dao.upsert(
            AiCacheEntity(
                normalizedMerchantKey = key,
                categoryId = result.categoryId,
                confidence = result.confidence,
                providerName = result.providerName,
                modelName = result.modelName,
                promptVersion = AiPromptBuilder.PROMPT_VERSION,
                resultVersion = result.responseVersion,
                explanation = result.explanation,
                createdAt = now,
                lastUsedAt = now,
                usageCount = 1,
                userAccepted = false,
                userRejected = false,
            )
        )
    }

    suspend fun markAccepted(normalizedMerchant: String) = withContext(Dispatchers.IO) {
        dao.markAccepted(MerchantNormalizer.normalize(normalizedMerchant))
    }

    suspend fun markRejected(normalizedMerchant: String) = withContext(Dispatchers.IO) {
        dao.markRejected(MerchantNormalizer.normalize(normalizedMerchant))
    }

    suspend fun invalidateCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteByCategoryId(categoryId)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}