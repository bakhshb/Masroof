package com.baraa.masroof.ai

/**
 * JVM-friendly in-memory implementation used by unit tests. Production
 * Android code uses [EncryptedAiSettingsStore] instead.
 */
class InMemoryAiSettingsStore : AiSettingsStore {
    private var key: String? = null

    override fun getApiKey(): String? = key
    override fun saveApiKey(key: String) { this.key = key }
    override fun deleteApiKey() { key = null }
    override fun hasApiKey(): Boolean = !key.isNullOrBlank()
}