package com.baraa.masroof.application

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.application.dashboard.DashboardService
import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.application.review.ReviewQueueUpdater
import com.baraa.masroof.application.review.ReviewWorkflowService
import com.baraa.masroof.application.transaction.TransactionReconciliationService
import com.baraa.masroof.application.transaction.TransactionReclassificationService
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.presentation.locale.AppLocaleContext
import com.baraa.masroof.application.onboarding.OnboardingPreferencesRepository
import com.baraa.masroof.data.preferences.SharedPrefsAppLocaleRepository
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.data.preferences.SharedPrefsOnboardingPreferencesRepository
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomManualReviewResolutionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.repository.RoomReviewRepository
import com.baraa.masroof.data.repository.RoomUserCorrectionRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipDiscoveryService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.ManualReviewResolutionRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.domain.repository.UserCorrectionRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.sms.datasource.AndroidSmsDataSource
import com.baraa.masroof.sms.datasource.SmsDataSource
import com.baraa.masroof.sms.ingestion.SmsIngestionResult
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.scanner.HistoricalSmsScanner
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Minimal manual composition root for P6–P9.
 *
 * No DI framework. Application-scoped database and repositories.
 * Does not use fallbackToDestructiveMigration.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val clock: InstantClock = InstantClock.System

    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: MasroofDatabase =
        Room.databaseBuilder(
            appContext,
            MasroofDatabase::class.java,
            MasroofDatabase.NAME,
        )
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .build()

    val rawSmsRepository: RawSmsRepository =
        RoomRawSmsRepository(database.rawSmsDao())

    val parsedEventRepository: ParsedEventRepository =
        RoomParsedEventRepository(database.parsedEventDao())

    val accountRegistryRepository: AccountRegistryRepository =
        RoomAccountRegistryRepository(database.accountRegistryDao())

    val cardRegistryRepository: CardRegistryRepository =
        RoomCardRegistryRepository(database.cardRegistryDao())

    val financialTransactionRepository: FinancialTransactionRepository =
        RoomFinancialTransactionRepository(
            dao = database.financialTransactionDao(),
            parsedEventDao = database.parsedEventDao(),
        )

    val reviewRepository: ReviewRepository =
        RoomReviewRepository(database.reviewItemDao())

    val userCorrectionRepository: UserCorrectionRepository =
        RoomUserCorrectionRepository(database.userCorrectionDao())

    val applicationContext: Context get() = appContext

    val localizedApplicationContext: Context
        get() = AppLocaleContext.wrap(
            appContext,
            AppLocaleContext.readStoredLanguageTag(appContext),
        )

    val onboardingPreferencesRepository: OnboardingPreferencesRepository =
        SharedPrefsOnboardingPreferencesRepository(
            appContext.getSharedPreferences(
                SharedPrefsOnboardingPreferencesRepository.PREFS_NAME,
                Context.MODE_PRIVATE,
            ),
        )

    val appLocaleRepository: AppLocaleRepository =
        SharedPrefsAppLocaleRepository(
            appContext.getSharedPreferences(
                SharedPrefsAppLocaleRepository.PREFS_NAME,
                Context.MODE_PRIVATE,
            ),
        )

    val ownershipDiscoveryService: OwnershipDiscoveryService =
        OwnershipDiscoveryService(
            accountRegistry = accountRegistryRepository,
            cardRegistry = cardRegistryRepository,
        )

    val ownershipResolver: OwnershipResolver =
        OwnershipResolver(
            accountRegistry = accountRegistryRepository,
            cardRegistry = cardRegistryRepository,
        )

    val ownershipConfirmationService: OwnershipConfirmationService =
        OwnershipConfirmationService(
            accountRegistry = accountRegistryRepository,
            cardRegistry = cardRegistryRepository,
        )

    val effectiveParsedEventProvider: EffectiveParsedEventProvider =
        EffectiveParsedEventProvider(
            parsedEventRepository = parsedEventRepository,
            userCorrectionRepository = userCorrectionRepository,
        )

    val transactionReconciliationService: TransactionReconciliationService =
        TransactionReconciliationService(
            parsedEventRepository = parsedEventRepository,
            rawSmsRepository = rawSmsRepository,
            financialTransactionRepository = financialTransactionRepository,
            ownershipResolver = ownershipResolver,
            effectiveParsedEventProvider = effectiveParsedEventProvider,
        )

    val reviewQueueUpdater: ReviewQueueUpdater =
        ReviewQueueUpdater(
            reviewRepository = reviewRepository,
            financialTransactionRepository = financialTransactionRepository,
            clock = clock,
        )

    val manualReviewResolutionRepository: ManualReviewResolutionRepository =
        RoomManualReviewResolutionRepository(
            database = database,
            financialTransactionRepository = financialTransactionRepository,
        )

    val reviewWorkflowService: ReviewWorkflowService =
        ReviewWorkflowService(
            reviewRepository = reviewRepository,
            userCorrectionRepository = userCorrectionRepository,
            financialTransactionRepository = financialTransactionRepository,
            rawSmsRepository = rawSmsRepository,
            ownershipResolver = ownershipResolver,
            effectiveParsedEventProvider = effectiveParsedEventProvider,
            reconciliationService = transactionReconciliationService,
            reviewQueueUpdater = reviewQueueUpdater,
            manualReviewResolutionRepository = manualReviewResolutionRepository,
            clock = clock,
        )

    val transactionReclassificationService: TransactionReclassificationService =
        TransactionReclassificationService(
            financialTransactionRepository = financialTransactionRepository,
            effectiveParsedEventProvider = effectiveParsedEventProvider,
            ownershipResolver = ownershipResolver,
        )

    val dashboardService: DashboardService =
        DashboardService(
            financialTransactionRepository = financialTransactionRepository,
            reviewRepository = reviewRepository,
            parsedEventRepository = parsedEventRepository,
            rawSmsRepository = rawSmsRepository,
            appLocaleRepository = appLocaleRepository,
        )

    val bankDetector: AlJaziraBankDetector = AlJaziraBankDetector()

    val parsingPipeline: AlJaziraParsingPipeline = AlJaziraParsingPipeline()

    val smsIngestionService: SmsIngestionService =
        SmsIngestionService(
            rawSmsRepository = rawSmsRepository,
            parsedEventRepository = parsedEventRepository,
            bankDetector = bankDetector,
            parseGateway = parsingPipeline,
            ownershipDiscovery = ownershipDiscoveryService,
            reconciliation = transactionReconciliationService,
            reviewQueueUpdater = reviewQueueUpdater,
        )

    val smsDataSource: SmsDataSource =
        AndroidSmsDataSource(appContext.contentResolver)

    val historicalSmsScanner: HistoricalSmsScanner =
        HistoricalSmsScanner(
            dataSource = smsDataSource,
            ingestionService = smsIngestionService,
        )

    /**
     * Discovers ownership candidates from all already-persisted ParsedEvents.
     */
    suspend fun discoverFromStoredEvents(): Int {
        var count = 0
        for (record in parsedEventRepository.listAll()) {
            ownershipDiscoveryService.observe(record.event)
            count++
        }
        return count
    }

    suspend fun reconcileStoredEvents() =
        transactionReconciliationService.reconcileStoredEvents()

    suspend fun refreshReviewQueue() =
        reviewWorkflowService.refreshReviewQueue()

    /**
     * Re-parses every stored RawSms that already has a ParsedEvent row.
     * Parser upgrades apply to the existing backlog without duplicating SMS evidence.
     */
    suspend fun reparseAllStoredEvents(): Int {
        var count = 0
        for (record in parsedEventRepository.listAll()) {
            val raw = rawSmsRepository.getById(record.event.rawSmsId) ?: continue
            when (smsIngestionService.reparseStored(raw)) {
                is SmsIngestionResult.Duplicate -> Unit
                is SmsIngestionResult.Failed -> Unit
                else -> count++
            }
        }
        discoverFromStoredEvents()
        reconcileStoredEvents()
        refreshReviewQueue()
        return count
    }

    fun close() {
        applicationScope.cancel()
        database.close()
    }
}
