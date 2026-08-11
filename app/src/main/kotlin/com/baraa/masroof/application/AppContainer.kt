package com.baraa.masroof.application

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.application.transaction.TransactionReconciliationService
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipDiscoveryService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.AccountRegistryRepository
import com.baraa.masroof.domain.repository.CardRegistryRepository
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.sms.datasource.AndroidSmsDataSource
import com.baraa.masroof.sms.datasource.SmsDataSource
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.scanner.HistoricalSmsScanner
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Minimal manual composition root for P6–P8.
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

    val transactionReconciliationService: TransactionReconciliationService =
        TransactionReconciliationService(
            parsedEventRepository = parsedEventRepository,
            rawSmsRepository = rawSmsRepository,
            financialTransactionRepository = financialTransactionRepository,
            ownershipResolver = ownershipResolver,
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

    fun close() {
        applicationScope.cancel()
        database.close()
    }
}
