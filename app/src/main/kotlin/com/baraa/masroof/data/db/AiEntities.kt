package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
            shareExactAmount = false,
            minimumConfidence = 80,
            requireHttps = true,
            timeoutMillis = 15_000L,
            hasApiKey = false,
        )
    }
}