package com.baraa.masroof.ai

/**
 * Persistent storage for the API key. The key MUST NEVER be stored in
 * plain text on disk.
 *
 * Two implementations:
 *  - [EncryptedAiSettingsStore] — Android implementation that uses
 *    `androidx.security:security-crypto`'s [EncryptedSharedPreferences].
 *  - [InMemoryAiSettingsStore] — JVM-friendly fake used by unit tests.
 *
 * The non-secret settings (base URL, model, confidence, etc.) live in
 * Room (see [AiSettingsEntity]) so they survive backups and migrations.
 * The API key itself lives here, separately, so that wiping the key does
 * not touch the rest of the settings.
 */
interface AiSettingsStore {
    /** Returns the saved API key, or null if none. */
    fun getApiKey(): String?

    /** Persist the API key. Existing value (if any) is replaced. */
    fun saveApiKey(key: String)

    /** Remove the saved API key. No-op if none. */
    fun deleteApiKey()

    /** True iff an API key is currently stored. */
    fun hasApiKey(): Boolean
}