package com.baraa.masroof

import android.app.Application
import com.baraa.masroof.ai.AiBatchCategorizationService
import com.baraa.masroof.ai.AiCacheRepository
import com.baraa.masroof.ai.AiCategorizationProvider
import com.baraa.masroof.ai.AiCategorizationService
import com.baraa.masroof.ai.AiHttpClient
import com.baraa.masroof.ai.AiProviderConfig
import com.baraa.masroof.ai.AiSettingsRepository
import com.baraa.masroof.ai.DisabledAiCategorizationProvider
import com.baraa.masroof.ai.EncryptedAiSettingsStore
import com.baraa.masroof.ai.OpenAiCompatibleProvider
import com.baraa.masroof.ai.RemoteAiHttpClient
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.repository.RoomCategoryRepository
import com.baraa.masroof.data.repository.RoomFinancialAccountRepository
import com.baraa.masroof.data.repository.RoomMerchantMemoryRepository
import com.baraa.masroof.data.repository.RoomTransactionRepository
import com.baraa.masroof.data.repository.TransactionImportService
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.sms.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Lazy-creates the Room database, all repositories,
 * the rule engine, and the import service. The default category list is
 * seeded on first launch. The AI layer is wired but disabled by default.
 */
class MasroofApplication : Application() {

    val database: MasroofDatabase by lazy { MasroofDatabase.build(this) }

    val transactionRepository: TransactionRepository by lazy {
        RoomTransactionRepository(database.transactionDao())
    }

    val categoryRepository: RoomCategoryRepository by lazy {
        RoomCategoryRepository(
            dao = database.categoryDao(),
            transactionCountByCategory = {
                transactionRepository.countByCategory()
                    .mapNotNull { (id, n) -> id?.let { it to n } }
                    .toMap()
            },
        )
    }

    val merchantMemoryRepository: RoomMerchantMemoryRepository by lazy {
        RoomMerchantMemoryRepository(database.merchantMemoryDao())
    }

    val financialAccountRepository: RoomFinancialAccountRepository by lazy {
        RoomFinancialAccountRepository(database.financialAccountDao())
    }

    val smsRepository: SmsRepository by lazy { SmsRepository(this) }

    val importService: TransactionImportService by lazy {
        TransactionImportService(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            merchantMemoryRepository = merchantMemoryRepository,
            financialAccountRepository = financialAccountRepository,
        )
    }

    // -- AI ----------------------------------------------------------------
    //
    // The AI layer is fully optional. Settings default to disabled; the
    // provider short-circuits until the user enables it.

    private val aiHttpClient: AiHttpClient by lazy { RemoteAiHttpClient() }
    private val aiEncryptedStore by lazy { EncryptedAiSettingsStore(this) }

    val aiSettingsRepository: AiSettingsRepository by lazy {
        AiSettingsRepository(
            dao = database.aiSettingsDao(),
            keyStore = aiEncryptedStore,
        )
    }

    val aiCacheRepository: AiCacheRepository by lazy {
        AiCacheRepository(database.aiCacheDao())
    }

    @Volatile
    private var cachedAiConfig: AiProviderConfig = AiProviderConfig()

    @Volatile
    private var cachedProvider: AiCategorizationProvider = DisabledAiCategorizationProvider()

    private val aiService: AiCategorizationService by lazy {
        AiCategorizationService(
            configProvider = { cachedAiConfig },
            provider = ProviderHolder(),
            cache = aiCacheRepository,
        )
    }

    /** Wraps the @Volatile provider so the service can delegate per-call. */
    private inner class ProviderHolder : AiCategorizationProvider {
        override val providerName: String get() = cachedProvider.providerName
        override suspend fun categorize(request: com.baraa.masroof.ai.AiCategorizationRequest) =
            cachedProvider.categorize(request)
    }

    /** Get the AI service with the latest config loaded. */
    fun aiCategorizationService(): AiCategorizationService {
        rebuildAiIfNeeded()
        return aiService
    }

    private fun rebuildAiIfNeeded() {
        val current = runCatching {
            kotlinx.coroutines.runBlocking { aiSettingsRepository.load() }
        }.getOrElse { AiProviderConfig() }
        if (current != cachedAiConfig) {
            cachedAiConfig = current
            cachedProvider = if (current.isReady) {
                OpenAiCompatibleProvider(current, aiHttpClient)
            } else {
                DisabledAiCategorizationProvider()
            }
        }
    }

    val aiBatchService: AiBatchCategorizationService by lazy {
        AiBatchCategorizationService(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            merchantMemoryRepository = merchantMemoryRepository,
            aiService = aiService,
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { categoryRepository.seedIfEmpty() }
            // Pre-load the AI config so the first batch call doesn't
            // need to block on a DB read.
            runCatching { rebuildAiIfNeeded() }
        }
    }
}