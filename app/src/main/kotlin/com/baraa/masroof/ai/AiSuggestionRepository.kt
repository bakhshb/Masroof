package com.baraa.masroof.ai

import com.baraa.masroof.data.db.AiSuggestion
import com.baraa.masroof.data.db.AiSuggestionDao
import com.baraa.masroof.data.db.AiSuggestionEntity
import com.baraa.masroof.data.db.CategoryDao
import com.baraa.masroof.data.db.TransactionDao
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.MerchantNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for the AI suggestion review queue
 * ("اقتراحات التصنيف الذكي").
 *
 * Each suggestion is bound to a transaction. Accepting applies the
 * category, marks the transaction `userConfirmed=true` and
 * `needsReview=false`, and updates the suggestion status to ACCEPTED.
 *
 * Rejecting marks the suggestion as REJECTED; the transaction is left
 * unclassified so the user can categorize it manually.
 */
open class AiSuggestionRepository(
    private val dao: AiSuggestionDao,
    private val transactionDao: TransactionDao? = null,
    private val categoryDao: CategoryDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Internal accessor for tests that need to seed the dao directly. */
    protected fun dao(): AiSuggestionDao = dao

    /** All suggestions, newest first. */
    fun observeAll(): Flow<List<AiSuggestion>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    /** Pending suggestions only (the review queue). */
    fun observePending(): Flow<List<AiSuggestion>> = dao.observePending().map { rows ->
        rows.map { it.toDomain() }
    }

    /** Suggestions matching the given status, newest first. */
    fun observeByStatus(status: String): Flow<List<AiSuggestion>> =
        dao.observeByStatus(status).map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: Long): AiSuggestion? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun getByTransactionId(transactionId: Long): List<AiSuggestion> =
        withContext(Dispatchers.IO) {
            dao.getByTransactionId(transactionId).map { it.toDomain() }
        }

    /** Insert a fresh suggestion tied to a transaction. */
    suspend fun insertFromResult(
        transaction: TransactionEntity,
        result: AiCategorizationResult,
    ): Long = withContext(Dispatchers.IO) {
        val now = now()
        dao.insert(
            AiSuggestionEntity(
                transactionId = transaction.id,
                merchantDisplay = transaction.merchantOrBeneficiary ?: "",
                amountBucket = bucketName(transaction.amount),
                currency = transaction.currency.name,
                categoryId = result.categoryId,
                categoryName = result.categoryName,
                confidence = result.confidence,
                explanation = result.explanation,
                providerName = result.providerName,
                modelName = result.modelName,
                promptVersion = AiPromptBuilder.PROMPT_VERSION,
                resultVersion = result.responseVersion,
                status = AiSuggestionEntity.STATUS_PENDING,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /**
     * Accept a suggestion. Validates the category still exists and is
     * enabled. If yes, applies it to the transaction; if no, marks the
     * suggestion as REJECTED with a synthetic reason and leaves the
     * transaction unclassified.
     *
     * Returns true if the category was applied, false otherwise.
     */
    suspend fun accept(suggestionId: Long): Boolean = withContext(Dispatchers.IO) {
        val s = dao.getById(suggestionId) ?: return@withContext false
        val txDao = transactionDao ?: return@withContext false
        val catDao = categoryDao ?: return@withContext false
        val category = catDao.getById(s.categoryId)
        if (category == null || !category.enabled) {
            dao.updateStatus(suggestionId, AiSuggestionEntity.STATUS_REJECTED, now())
            return@withContext false
        }
        // Apply category + flags to the transaction.
        val tx = txDao.getById(s.transactionId) ?: return@withContext false
        val updated = tx.copy(
            categoryId = s.categoryId,
            categorySource = CategorySource.AI,
            categoryConfidence = s.confidence,
            userConfirmed = true,
            needsReview = false,
            updatedAt = now(),
        )
        transactionDao.update(updated)
        dao.updateStatus(suggestionId, AiSuggestionEntity.STATUS_ACCEPTED, now())
        true
    }

    /**
     * Modify a suggestion by picking a different category. Source is set
     * to USER so the deterministic engine / future AI suggestions don't
     * override the user's choice.
     */
    suspend fun modify(
        suggestionId: Long,
        newCategoryId: Long,
        newCategoryName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val s = dao.getById(suggestionId) ?: return@withContext false
        val txDao = transactionDao ?: return@withContext false
        val catDao = categoryDao ?: return@withContext false
        val category = catDao.getById(newCategoryId)
        if (category == null || !category.enabled) return@withContext false
        val tx = txDao.getById(s.transactionId) ?: return@withContext false
        val updated = tx.copy(
            categoryId = newCategoryId,
            categorySource = CategorySource.USER,
            categoryConfidence = 100,
            userConfirmed = true,
            needsReview = false,
            updatedAt = now(),
        )
        transactionDao.update(updated)
        // Update the suggestion to MODIFIED status with the new category.
        dao.update(
            s.copy(
                categoryId = newCategoryId,
                categoryName = newCategoryName,
                status = AiSuggestionEntity.STATUS_MODIFIED,
                updatedAt = now(),
            )
        )
        true
    }

    /**
     * Reject a suggestion. The transaction is left unclassified so the
     * user can categorize it manually. The provider will not be called
     * again for the same merchant + prompt version (cache marker).
     */
    suspend fun reject(suggestionId: Long) {
        withContext(Dispatchers.IO) {
            val s = dao.getById(suggestionId) ?: return@withContext
            dao.updateStatus(suggestionId, AiSuggestionEntity.STATUS_REJECTED, now())
        }
    }

    /**
     * Filter helper for the queue UI:
     *  - ALL
     *  - HIGH (confidence >= threshold)
     *  - LOW (confidence < threshold)
     *  - REJECTED
     */
    enum class QueueFilter { ALL, HIGH, LOW, REJECTED }

    fun observeFiltered(filter: QueueFilter, minimumConfidence: Int): Flow<List<AiSuggestion>> =
        when (filter) {
            QueueFilter.ALL -> observePending()
            QueueFilter.HIGH -> observePending().map { list ->
                list.filter { it.confidence >= minimumConfidence }
            }
            QueueFilter.LOW -> observePending().map { list ->
                list.filter { it.confidence < minimumConfidence }
            }
            QueueFilter.REJECTED -> observeByStatus(AiSuggestionEntity.STATUS_REJECTED)
        }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { dao.deleteAll() }
    }
}

private fun AiSuggestionEntity.toDomain(): AiSuggestion = AiSuggestion(
    id = id,
    transactionId = transactionId,
    merchantDisplay = merchantDisplay,
    amountBucket = amountBucket,
    currency = currency,
    categoryId = categoryId,
    categoryName = categoryName,
    confidence = confidence,
    explanation = explanation,
    providerName = providerName,
    modelName = modelName,
    promptVersion = promptVersion,
    resultVersion = resultVersion,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun bucketName(amount: java.math.BigDecimal?): String {
    if (amount == null) return AmountBucket.UNDER_50.name
    return AmountBucket.bucket(amount.toDouble()).name
}

/**
 * Compute the merchant key safely — empty / whitespace-only inputs
 * return null so callers can skip the suggestion.
 */
internal fun safeMerchantKey(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val key = MerchantNormalizer.normalize(raw)
    return key.takeIf { it.isNotBlank() }
}

/** Common financial-treatment classification helpers. */
internal fun isReviewableTreatment(t: FinancialTreatment): Boolean =
    t == FinancialTreatment.EXPENSE || t == FinancialTreatment.BANK_FEE