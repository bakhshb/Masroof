package com.baraa.masroof.ai

import com.baraa.masroof.data.db.AiSettingsDao
import com.baraa.masroof.data.db.AiSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for AI provider settings.
 *
 * The non-secret fields (baseUrl, modelName, enabled, shareExactAmount,
 * minimumConfidence, etc.) live in Room. The API key is held only by
 * [AiSettingsStore] — this repository never sees the key as plain text
 * after construction, and never persists it.
 */
class AiSettingsRepository(
    private val dao: AiSettingsDao,
    private val keyStore: AiSettingsStore,
) {

    suspend fun load(): AiProviderConfig = withContext(Dispatchers.IO) {
        val stored = dao.get() ?: AiSettingsEntity.DEFAULTS
        AiProviderConfig(
            enabled = stored.enabled,
            deploymentMode = runCatching {
                AiDeploymentMode.valueOf(stored.deploymentMode)
            }.getOrDefault(AiDeploymentMode.REMOTE),
            providerLabel = stored.providerLabel,
            baseUrl = stored.baseUrl,
            modelName = stored.modelName,
            onDeviceModelPath = stored.onDeviceModelPath,
            apiKey = keyStore.getApiKey().orEmpty(),
            shareExactAmount = stored.shareExactAmount,
            minimumConfidence = stored.minimumConfidence,
            requireHttps = stored.requireHttps,
            timeoutMillis = stored.timeoutMillis,
        )
    }

    suspend fun saveNonSecret(config: AiProviderConfig): Unit = withContext(Dispatchers.IO) {
        val entity = AiSettingsEntity(
            id = AiSettingsEntity.SINGLETON_ID,
            enabled = config.enabled,
            providerLabel = config.providerLabel,
            baseUrl = config.baseUrl,
            modelName = config.modelName,
            deploymentMode = config.deploymentMode.name,
            onDeviceModelPath = config.onDeviceModelPath,
            shareExactAmount = config.shareExactAmount,
            minimumConfidence = config.minimumConfidence,
            requireHttps = config.requireHttps,
            timeoutMillis = config.timeoutMillis,
            hasApiKey = keyStore.hasApiKey(),
        )
        dao.upsert(entity)
    }

    suspend fun saveApiKey(key: String) = withContext(Dispatchers.IO) {
        keyStore.saveApiKey(key)
        // Update the hasApiKey flag in Room so the UI shows the saved state.
        val current = dao.get() ?: AiSettingsEntity.DEFAULTS
        dao.upsert(current.copy(hasApiKey = true))
    }

    suspend fun deleteApiKey() = withContext(Dispatchers.IO) {
        keyStore.deleteApiKey()
        val current = dao.get() ?: AiSettingsEntity.DEFAULTS
        dao.upsert(current.copy(hasApiKey = false))
    }

    /**
     * Clear all persisted AI state: settings row + cache. Used by the
     * "delete cached AI results" maintenance action.
     */
    suspend fun clearCache(cacheDao: com.baraa.masroof.data.db.AiCacheDao) = withContext(Dispatchers.IO) {
        cacheDao.deleteAll()
    }
}