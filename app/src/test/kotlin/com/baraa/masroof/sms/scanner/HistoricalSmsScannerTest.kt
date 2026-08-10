package com.baraa.masroof.sms.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.sms.datasource.SmsDataSource
import com.baraa.masroof.sms.datasource.SmsPermissionException
import com.baraa.masroof.sms.datasource.SmsProviderException
import com.baraa.masroof.sms.ingestion.SmsIngestionService
import com.baraa.masroof.sms.model.ProviderSmsRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoricalSmsScannerTest {

    private lateinit var db: MasroofDatabase
    private lateinit var ingestion: SmsIngestionService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingestion = SmsIngestionService(
            rawSmsRepository = RoomRawSmsRepository(db.rawSmsDao()),
            parsedEventRepository = RoomParsedEventRepository(db.parsedEventDao()),
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun processesOldestToNewest_andCountsOutcomes() = runBlocking {
        val t1 = Instant.parse("2026-08-01T10:00:00Z")
        val t2 = Instant.parse("2026-08-02T10:00:00Z")
        val purchase = purchaseBody()
        val otp = "رمز التحقق الخاص بك هو 482911. لا تشاركه مع أي شخص."
        val after = Instant.parse("2026-07-01T00:00:00Z")
        val order = mutableListOf<String>()
        val source = FakeSmsDataSource(
            records = listOf(
                ProviderSmsRecord("1", "AlJazira", purchase, t1),
                ProviderSmsRecord("2", "OtherBank", purchase, t1),
                ProviderSmsRecord("3", "AlJazira", otp, t2),
                ProviderSmsRecord("1", "AlJazira", purchase, t1), // duplicate provider row
            ),
            onQuery = { receivedAfter -> order += "after=${receivedAfter?.toEpochMilli()}" },
        )
        val scanner = HistoricalSmsScanner(source, ingestion)
        val result = scanner.scan(receivedAfter = after)
        assertEquals(listOf("after=${after.toEpochMilli()}"), order)
        assertEquals(4, result.scanned)
        assertEquals(1, result.parsed)
        assertEquals(1, result.nonFinancial)
        assertEquals(1, result.notRelevant)
        assertEquals(1, result.duplicates)
        assertEquals(2, db.rawSmsDao().count())
        assertNull(result.failure)
    }

    @Test
    fun permissionDenied_isDistinctFailure() = runBlocking {
        val source = object : SmsDataSource {
            override fun queryInbox(receivedAfter: Instant?) = throw SmsPermissionException()
        }
        val result = HistoricalSmsScanner(source, ingestion).scan()
        assertEquals(SmsScanFailure.PermissionDenied, result.failure)
        assertEquals(0, result.scanned)
    }

    @Test
    fun providerError_isDistinctFailure() = runBlocking {
        val source = object : SmsDataSource {
            override fun queryInbox(receivedAfter: Instant?) =
                throw SmsProviderException("boom")
        }
        val result = HistoricalSmsScanner(source, ingestion).scan()
        assertTrue(result.failure is SmsScanFailure.ProviderError)
        assertEquals(0, result.scanned)
    }

    @Test
    fun malformedRow_skippedWithoutAbortingScan() = runBlocking {
        val good = ProviderSmsRecord("1", "AlJazira", purchaseBody(), Instant.parse("2026-08-01T00:00:00Z"))
        val bad = ProviderSmsRecord("2", "AlJazira", "", Instant.parse("2026-08-02T00:00:00Z"))
        val source = FakeSmsDataSource(listOf(good, bad))
        val result = HistoricalSmsScanner(source, ingestion).scan()
        assertEquals(2, result.scanned)
        assertEquals(1, result.skippedMalformed)
        assertEquals(1, result.parsed)
        assertEquals(1, db.rawSmsDao().count())
    }

    private fun purchaseBody() = """
        شراء عبر الانترنت
        بطاقة: 7271
        لدى: Keeta
        بمبلغ: 51.99 SAR
        في: 14:32 03-08-2026
    """.trimIndent()

    private class FakeSmsDataSource(
        private val records: List<ProviderSmsRecord>,
        private val onQuery: (Instant?) -> Unit = {},
    ) : SmsDataSource {
        override fun queryInbox(receivedAfter: Instant?): Sequence<ProviderSmsRecord> {
            onQuery(receivedAfter)
            return records
                .filter { receivedAfter == null || !it.receivedAt.isBefore(receivedAfter) }
                .sortedBy { it.receivedAt }
                .asSequence()
        }
    }
}
