package com.baraa.masroof.application

import android.content.Context
import androidx.room.Room
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.repository.RawSmsRepository
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.sms.datasource.AndroidSmsDataSource
import com.baraa.masroof.sms.datasource.SmsDataSource
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.scanner.HistoricalSmsScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Minimal manual composition root for P6.
 *
 * No DI framework. Application-scoped database and repositories.
 * Does not use fallbackToDestructiveMigration.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: MasroofDatabase =
        Room.databaseBuilder(
            appContext,
            MasroofDatabase::class.java,
            MasroofDatabase.NAME,
        ).build()

    val rawSmsRepository: RawSmsRepository =
        RoomRawSmsRepository(database.rawSmsDao())

    val parsedEventRepository: ParsedEventRepository =
        RoomParsedEventRepository(database.parsedEventDao())

    val bankDetector: AlJaziraBankDetector = AlJaziraBankDetector()

    val parsingPipeline: AlJaziraParsingPipeline = AlJaziraParsingPipeline()

    val smsIngestionService: SmsIngestionService =
        SmsIngestionService(
            rawSmsRepository = rawSmsRepository,
            parsedEventRepository = parsedEventRepository,
            bankDetector = bankDetector,
            parseGateway = parsingPipeline,
        )

    val smsDataSource: SmsDataSource =
        AndroidSmsDataSource(appContext.contentResolver)

    val historicalSmsScanner: HistoricalSmsScanner =
        HistoricalSmsScanner(
            dataSource = smsDataSource,
            ingestionService = smsIngestionService,
        )

    fun close() {
        applicationScope.cancel()
        database.close()
    }
}
