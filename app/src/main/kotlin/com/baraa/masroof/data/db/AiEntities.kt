package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// TransactionEntity lives in this same package — no import needed.

/**
 * Cached AI categorization. Stores only the **sanitized** fields:
 *  - normalized merchant key
 *  - category id, confidence, provider name, model name, prompt / result
 *    versions, timestamps, usage stats, accepted / rejected flags
 *
 * **Never** stores the raw prompt, the raw response body, the API key,
 * account numbers, or exact amounts.
 *
 * Invalidation rules:
 *  - when the underlying category is disabled or deleted, rows pointing
 *    to it become unusable (the cache lookup considers [categoryEnabled]
 *    at read time)
 *  - when [promptVersion] changes (a new schema is published), the
 *    service treats old rows as misses and refetches
 */
@Entity(
    tableName = "ai_cache",
    indices = [
        Index(value = ["normalizedMerchantKey"]),
    ],
)
data class AiCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "normalizedMerchantKey")
    val normalizedMerchantKey: String,

    @ColumnInfo(name = "categoryId")
    val categoryId: Long,

    @ColumnInfo(name = "confidence")
    val confidence: Int,

    @ColumnInfo(name = "providerName")
    val providerName: String,

    @ColumnInfo(name = "modelName")
    val modelName: String,

    @ColumnInfo(name = "promptVersion")
    val promptVersion: String,

    @ColumnInfo(name = "resultVersion")
    val resultVersion: String,

    @ColumnInfo(name = "explanation")
    val explanation: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "lastUsedAt")
    val lastUsedAt: Long,

    @ColumnInfo(name = "usageCount")
    val usageCount: Int,

    @ColumnInfo(name = "userAccepted")
    val userAccepted: Boolean = false,

    @ColumnInfo(name = "userRejected")
    val userRejected: Boolean = false,
)

/**
 * Persisted AI provider settings. **The API key is NEVER stored here.**
 * The key is stored via [AiSettingsStore] in Keystore-backed encrypted
 * shared preferences. This entity only holds the non-secret knobs.
 */
@Entity(tableName = "ai_settings")
data class AiSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "providerLabel")
    val providerLabel: String,

    @ColumnInfo(name = "baseUrl")
    val baseUrl: String,

    @ColumnInfo(name = "modelName")
    val modelName: String,

    @ColumnInfo(name = "deploymentMode")
    val deploymentMode: String = "REMOTE",

    @ColumnInfo(name = "onDeviceModelPath")
    val onDeviceModelPath: String = "",

    @ColumnInfo(name = "shareExactAmount")
    val shareExactAmount: Boolean,

    @ColumnInfo(name = "minimumConfidence")
    val minimumConfidence: Int,

    @ColumnInfo(name = "requireHttps")
    val requireHttps: Boolean,

    @ColumnInfo(name = "timeoutMillis")
    val timeoutMillis: Long,

    @ColumnInfo(name = "hasApiKey")
    val hasApiKey: Boolean,
) {
    companion object {
        const val SINGLETON_ID: Int = 1

        /** Defaults — AI disabled, exact-amount disabled, confidence ≥ 80. */
        val DEFAULTS: AiSettingsEntity = AiSettingsEntity(
            id = SINGLETON_ID,
            enabled = false,
            providerLabel = "OpenAI-compatible",
            baseUrl = "https://api.openai.com",
            modelName = "gpt-4o-mini",
            deploymentMode = "REMOTE",
            onDeviceModelPath = "",
            shareExactAmount = false,
            minimumConfidence = 80,
            requireHttps = true,
            timeoutMillis = 15_000L,
            hasApiKey = false,
        )
    }
}

/**
 * A pending AI suggestion attached to a transaction. Drives the
 * review queue UI: اقتراحات التصنيف الذكي.
 *
 * Sanitized — only safe fields are persisted (no raw prompts or
 * responses, no API key, no merchant raw text beyond the display name,
 * no exact amounts).
 *
 * When the user accepts / rejects / modifies the suggestion, the
 * corresponding [status] is updated and the underlying transaction's
 * `categoryId` / `categorySource` / `userConfirmed` / `needsReview`
 * fields are kept in sync.
 */
@Entity(
    tableName = "ai_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class AiSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transactionId")
    val transactionId: Long,

    @ColumnInfo(name = "merchantDisplay")
    val merchantDisplay: String,

    @ColumnInfo(name = "amountBucket")
    val amountBucket: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "categoryId")
    val categoryId: Long,

    @ColumnInfo(name = "categoryName")
    val categoryName: String,

    @ColumnInfo(name = "confidence")
    val confidence: Int,

    @ColumnInfo(name = "explanation")
    val explanation: String,

    @ColumnInfo(name = "providerName")
    val providerName: String,

    @ColumnInfo(name = "modelName")
    val modelName: String,

    @ColumnInfo(name = "promptVersion")
    val promptVersion: String,

    @ColumnInfo(name = "resultVersion")
    val resultVersion: String,

    /**
     * One of PENDING, ACCEPTED, REJECTED, MODIFIED.
     */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
) {
    companion object {
        const val STATUS_PENDING: String = "PENDING"
        const val STATUS_ACCEPTED: String = "ACCEPTED"
        const val STATUS_REJECTED: String = "REJECTED"
        const val STATUS_MODIFIED: String = "MODIFIED"
    }
}

/** Domain-level read model for the AI suggestion queue. */
data class AiSuggestion(
    val id: Long,
    val transactionId: Long,
    val merchantDisplay: String,
    val amountBucket: String,
    val currency: String,
    val categoryId: Long,
    val categoryName: String,
    val confidence: Int,
    val explanation: String,
    val providerName: String,
    val modelName: String,
    val promptVersion: String,
    val resultVersion: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)