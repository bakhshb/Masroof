package com.baraa.masroof

import android.app.Application
import com.baraa.masroof.ai.AiBatchCategorizationService
import com.baraa.masroof.ai.AiCacheRepository
import com.baraa.masroof.ai.AiCategorizationProvider
import com.baraa.masroof.ai.AiCategorizationService
import com.baraa.masroof.ai.AiDeploymentMode
import com.baraa.masroof.ai.AiHttpClient
import com.baraa.masroof.ai.AiProviderConfig
import com.baraa.masroof.ai.AiSettingsRepository
import com.baraa.masroof.ai.AiSuggestionRepository
import com.baraa.masroof.ai.DisabledAiCategorizationProvider
import com.baraa.masroof.ai.EncryptedAiSettingsStore
import com.baraa.masroof.ai.OnDeviceLinkAssistProvider
import com.baraa.masroof.ai.OnDeviceModelStore
import com.baraa.masroof.ai.OpenAiCompatibleProvider
import com.baraa.masroof.ai.RemoteAiHttpClient
import com.baraa.masroof.diagnostics.DiagnosticCollector
import com.baraa.masroof.diagnostics.DiagnosticErrorLog
import com.baraa.masroof.diagnostics.FakeSmsSamples
import com.baraa.masroof.diagnostics.DeveloperPreferences
import com.baraa.masroof.diagnostics.SharedPreferencesDeveloperPreferences
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.repository.RoomCategoryRepository
import com.baraa.masroof.data.repository.RoomFinancialAccountRepository
import com.baraa.masroof.data.repository.RoomMerchantMemoryRepository
import com.baraa.masroof.data.repository.RoomTransactionRepository
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.sms.SmsRepository
import com.baraa.masroof.ledger.SystemAccountSeeder
import com.baraa.masroof.ledger.LedgerRepository
import com.baraa.masroof.ledger.JournalGenerationService
import com.baraa.masroof.ledger.TransactionLinkingService
import com.baraa.masroof.ledger.AccountLinkRuleRepository
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

    /**
     * Onboarding persistence: single source of truth for
     * `onboardingCompleted`, `onboardingVersion`, `lastCompletedStep`,
     * and `completedAt`. UI consumers subscribe to its Flow rather than
     * guessing from `FinancialSetup.setupCompleted`.
     */
    val onboardingRepository: com.baraa.masroof.ui.onboarding.OnboardingRepository by lazy {
        com.baraa.masroof.ui.onboarding.SharedPreferencesOnboardingRepository(
            context = this,
            permissionStore = smsPermissionStore,
        )
    }

    /**
     * Tracks the OS-level READ_SMS state. Decoupled from onboarding so
     * revoking the permission never re-opens onboarding.
     */
    val smsPermissionStore: com.baraa.masroof.ui.onboarding.SmsPermissionStore by lazy {
        com.baraa.masroof.ui.onboarding.SmsPermissionStore(this)
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

    @Volatile
    private var cachedLinkAssist: OnDeviceLinkAssistProvider? = null

    private fun rebuildAiIfNeeded(force: Boolean = false) {
        val current = runCatching {
            kotlinx.coroutines.runBlocking { aiSettingsRepository.load() }
        }.getOrElse { AiProviderConfig() }
        val path = current.onDeviceModelPath.ifBlank {
            OnDeviceModelStore.defaultModelPath(filesDir)
        }
        val wantOnDevice = current.enabled &&
            current.deploymentMode == AiDeploymentMode.ON_DEVICE
        val haveLinkAssist = cachedLinkAssist != null
        if (!force && current == cachedAiConfig && wantOnDevice == haveLinkAssist) {
            return
        }
        cachedAiConfig = current
        cachedLinkAssist = null
        cachedProvider = when {
            !current.enabled -> DisabledAiCategorizationProvider()
            wantOnDevice -> {
                // SMS-heuristic link assist only — no native LLM (device crashes).
                cachedLinkAssist = runCatching {
                    OnDeviceLinkAssistProvider(current.copy(onDeviceModelPath = path))
                }.getOrNull()
                DisabledAiCategorizationProvider()
            }
            current.isRemoteReady -> OpenAiCompatibleProvider(current, aiHttpClient)
            else -> DisabledAiCategorizationProvider()
        }
    }

    /** On-device link assist when deployment mode is ON_DEVICE. */
    fun onDeviceLinkAssistProvider(): OnDeviceLinkAssistProvider? {
        rebuildAiIfNeeded()
        return cachedLinkAssist?.takeIf { it.isReady() }
    }

    /**
     * Status check for on-device mode. Native LLM inference is disabled for
     * stability; link assist uses the local SMS body only.
     */
    suspend fun probeOnDeviceModel(): String {
        rebuildAiIfNeeded(force = true)
        val cfg = cachedAiConfig
        if (!cfg.enabled) return "فعّل الذكاء الاصطناعي أولًا"
        if (cfg.deploymentMode != AiDeploymentMode.ON_DEVICE) {
            return "فعّل وضع «على الجهاز» أولًا"
        }
        return if (cachedLinkAssist?.isReady() == true) {
            "جاهز: اقتراح الربط من نص الرسالة محليًا (بدون تشغيل نموذج ثقيل)"
        } else {
            "احفظ الإعدادات ثم أعد المحاولة"
        }
    }

    val aiBatchService: AiBatchCategorizationService by lazy {
        AiBatchCategorizationService(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            merchantMemoryRepository = merchantMemoryRepository,
            aiService = aiService,
            suggestionRepository = aiSuggestionRepository,
        )
    }

    val aiSuggestionRepository: AiSuggestionRepository by lazy {
        AiSuggestionRepository(
            dao = database.aiSuggestionDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
        )
    }

    /**
     * Global sanitized error log. Capped at 100 entries; cleared on
     * process death. Records only error category + short Arabic message
     * — no stack traces, no transaction data, no API keys.
     */
    val errorLog: DiagnosticErrorLog = DiagnosticErrorLog()

    val diagnosticCollector: DiagnosticCollector by lazy {
        DiagnosticCollector(
            context = this,
            database = database,
            merchantMemoryRepository = merchantMemoryRepository,
            categoryRepository = categoryRepository,
            aiSettingsRepository = aiSettingsRepository,
            errorLog = errorLog,
        )
    }

    /**
     * Test data mode toggle. Defaults to disabled. When enabled, the
     * UI exposes the bundled fake SMS samples and runs them through the
     * parser pipeline WITHOUT persisting to the real transactions table.
     */
    @Volatile
    var testDataMode: Boolean = false

    val fakeSmsSamples: List<FakeSmsSamples.Sample> = FakeSmsSamples.samples

    val developerPreferences: DeveloperPreferences by lazy {
        SharedPreferencesDeveloperPreferences(this)
    }

    val themePreferenceRepository: com.baraa.masroof.ui.theme.ThemePreferenceRepository by lazy {
        com.baraa.masroof.ui.theme.SharedPreferencesThemePreferenceRepository(this)
    }

    val financialSetupRepository: com.baraa.masroof.data.repository.FinancialSetupRepository by lazy {
        com.baraa.masroof.data.repository.RoomFinancialSetupRepository(database.financialSetupDao())
    }

    val accountLinkRuleRepository: AccountLinkRuleRepository by lazy { AccountLinkRuleRepository(database.accountLinkRuleDao()) }
    val accountIdentifierRepository: com.baraa.masroof.data.repository.AccountIdentifierRepository by lazy {
        com.baraa.masroof.data.repository.AccountIdentifierRepository(
            dao = database.accountIdentifierDao(),
            accountDao = database.financialAccountDao(),
            senderProfileDao = database.senderProfileDao(),
            accountSenderDao = database.accountSenderProfileDao(),
        )
    }

    val senderProfileRepository: com.baraa.masroof.data.repository.SenderProfileRepository by lazy {
        com.baraa.masroof.data.repository.SenderProfileRepository(
            dao = database.senderProfileDao(),
            accountSenderDao = database.accountSenderProfileDao(),
            accountDao = database.financialAccountDao(),
            mappingDao = database.senderInstitutionMappingDao(),
        )
    }

    val messagePatternRepository: com.baraa.masroof.data.repository.MessagePatternRepository by lazy {
        com.baraa.masroof.data.repository.MessagePatternRepository(
            definitionDao = database.messagePatternDefinitionDao(),
            fieldDao = database.patternFieldDefinitionDao(),
        )
    }

    val linkPatternSuggester: com.baraa.masroof.ledger.LinkPatternSuggester by lazy {
        com.baraa.masroof.ledger.LinkPatternSuggester(
            identifierRepository = accountIdentifierRepository,
            rules = accountLinkRuleRepository,
            smsBodyRepository = transactionSmsBodyRepository,
        )
    }
    val senderInstitutionMappingRepository: com.baraa.masroof.data.repository.SenderInstitutionMappingRepository by lazy {
        com.baraa.masroof.data.repository.RoomSenderInstitutionMappingRepository(
            dao = database.senderInstitutionMappingDao(),
            senderNormalizer = { sender -> com.baraa.masroof.ledger.FinancialInstitutionResolver.senderKey(sender) },
        )
    }
    val institutionResolver: com.baraa.masroof.ledger.FinancialInstitutionResolver by lazy {
        com.baraa.masroof.ledger.FinancialInstitutionResolver(database.senderInstitutionMappingDao())
    }
    val systemAccountSeeder: SystemAccountSeeder by lazy { SystemAccountSeeder(database.financialAccountDao()) }
    val ledgerRepository: LedgerRepository by lazy { LedgerRepository(database) }
    val journalGenerationService: JournalGenerationService by lazy {
        JournalGenerationService(systemAccounts = { key -> systemAccountSeeder.accountId(key) })
    }
    val transactionLinkingService: TransactionLinkingService by lazy {
        TransactionLinkingService(transactionRepository, ledgerRepository, journalGenerationService, accountIdentifierRepository, accountLinkRuleRepository)
    }

    val transactionCorrectionService: com.baraa.masroof.ledger.TransactionCorrectionService by lazy {
        com.baraa.masroof.ledger.TransactionCorrectionService(
            transactions = transactionRepository,
            journalReverser = com.baraa.masroof.ledger.JournalReverser { journalId ->
                ledgerRepository.reverse(journalId)
            },
        )
    }

    val historicalAccountRelinkService: com.baraa.masroof.ledger.HistoricalAccountRelinkService by lazy {
        com.baraa.masroof.ledger.HistoricalAccountRelinkService(
            transactionRepository = transactionRepository,
            financialAccountRepository = financialAccountRepository,
            identifierRepository = accountIdentifierRepository,
            journalGenerationService = journalGenerationService,
            ledgerRepository = ledgerRepository,
            systemAccounts = { key -> systemAccountSeeder.accountId(key) },
        )
    }

    val importResetService: com.baraa.masroof.ledger.ImportResetService by lazy {
        com.baraa.masroof.ledger.ImportResetService(database)
    }

    /**
     * Atomic, single-Room-transaction SMS importer. Replaces the previous
     * two-stage `importService.preview/commit` flow that left transactions
     * `NEEDS_REVIEW` and never auto-posted their postings, which is why
     * account balances never moved in the UI. Each `import()` call returns
     * the **structured result** with actual posted-journal counts so the
     * UI cannot claim "linked N transactions" without evidence.
     */
    val transactionSmsBodyRepository: com.baraa.masroof.data.repository.TransactionSmsBodyRepository by lazy {
        com.baraa.masroof.data.repository.TransactionSmsBodyRepository(database.transactionSmsBodyDao())
    }

    /** Kept for schema/tests only — production training uses [messagePatternRepository]. */
    @Deprecated("Use messagePatternRepository / BankMessagesScreen")
    val senderMessagePatternRepository: com.baraa.masroof.data.repository.SenderMessagePatternRepository by lazy {
        com.baraa.masroof.data.repository.SenderMessagePatternRepository(
            dao = database.senderMessagePatternDao(),
        )
    }

    val importOrchestrator: com.baraa.masroof.data.repository.SmsImportOrchestrator by lazy {
        com.baraa.masroof.data.repository.SmsImportOrchestrator(
            database = database,
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            merchantMemoryRepository = merchantMemoryRepository,
            accountIdentifierRepository = accountIdentifierRepository,
            accountMatcher = com.baraa.masroof.ledger.AccountMatcher,
            journalGenerationService = journalGenerationService,
            ledgerRepository = ledgerRepository,
            systemAccounts = { key -> systemAccountSeeder.accountId(key) },
            institutionResolver = institutionResolver,
            smsBodyRepository = transactionSmsBodyRepository,
            senderProfileRepository = senderProfileRepository,
            messagePatternRepository = messagePatternRepository,
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { categoryRepository.seedIfEmpty() }
            runCatching { systemAccountSeeder.seed() }
            runCatching { accountIdentifierRepository.ensureLegacyIdentifierBackfill() }
            // Pre-load the AI config so the first batch call doesn't
            // need to block on a DB read.
            runCatching { rebuildAiIfNeeded() }
        }
    }
}