package com.baraa.masroof.sms.ingestion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParseResult
import com.baraa.masroof.parsing.model.SmsParseInput
import com.baraa.masroof.parsing.parser.SmsParseGateway
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.sms.mapper.AndroidSmsMapper
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmsIngestionServiceTest {

    private lateinit var db: MasroofDatabase
    private lateinit var rawRepo: RoomRawSmsRepository
    private lateinit var parsedRepo: RoomParsedEventRepository
    private lateinit var service: SmsIngestionService
    private val parseCalls = AtomicInteger(0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        parseCalls.set(0)
        val countingGateway = SmsParseGateway { input ->
            parseCalls.incrementAndGet()
            AlJaziraParsingPipeline().parse(input)
        }
        service = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = countingGateway,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstAlJaziraMessage_insertsAndPersistsParsedEvent() = runBlocking {
        val raw = aljaziraPurchase(id = "android-sms:1", deviceId = "1")
        val result = service.ingest(raw)
        assertTrue(result is SmsIngestionResult.Parsed)
        assertEquals(raw, rawRepo.getById(raw.id))
        val record = parsedRepo.findByRawSmsId(raw.id)!!
        assertEquals(MessageFamily.PURCHASE, record.event.messageFamily)
        assertEquals(ParseStatus.SUCCESS, record.event.parseStatus)
        assertEquals(Money.of("51.99", Currency.SAR), record.event.amount)
        assertEquals(1, parseCalls.get())
    }

    @Test
    fun duplicate_doesNotReparseOrDuplicateEvent() = runBlocking {
        val raw = aljaziraPurchase(id = "android-sms:2", deviceId = "2")
        assertTrue(service.ingest(raw) is SmsIngestionResult.Parsed)
        assertEquals(1, parseCalls.get())
        assertEquals(SmsIngestionResult.Duplicate, service.ingest(raw))
        assertEquals(1, parseCalls.get())
        assertEquals(1, db.rawSmsDao().count())
        assertEquals(1, db.parsedEventDao().count())
    }

    @Test
    fun reviewRequired_persistsEvent() = runBlocking {
        val body = "تنبيه بنك الجزيرة: حدث تحديث في خدماتك. راجع التطبيق للتفاصيل."
        val raw = RawSms(
            id = "android-sms:3",
            sender = "AlJazira",
            body = body,
            receivedAt = Instant.parse("2026-08-03T08:00:00Z"),
            deviceMessageId = "3",
            bodyHash = SmsBodyHasher.sha256Hex(body),
        )
        val result = service.ingest(raw)
        assertTrue(result is SmsIngestionResult.ReviewRequired)
        assertEquals(
            ParseStatus.REVIEW_REQUIRED,
            parsedRepo.findByRawSmsId(raw.id)!!.event.parseStatus,
        )
    }

    @Test
    fun nonFinancial_persistsEvent() = runBlocking {
        val body = "رمز التحقق الخاص بك هو 482911. لا تشاركه مع أي شخص."
        val raw = RawSms(
            id = "android-sms:4",
            sender = "AlJazira",
            body = body,
            receivedAt = Instant.parse("2026-08-03T08:00:00Z"),
            deviceMessageId = "4",
            bodyHash = SmsBodyHasher.sha256Hex(body),
        )
        val result = service.ingest(raw)
        assertTrue(result is SmsIngestionResult.NonFinancial)
        assertEquals(ParseStatus.NON_FINANCIAL, parsedRepo.findByRawSmsId(raw.id)!!.event.parseStatus)
        assertNull(parsedRepo.findByRawSmsId(raw.id)!!.event.amount)
    }

    @Test
    fun nearMissSender_notPersisted() = runBlocking {
        listOf("JaziraNews", "NotAlJazira", "OtherBank").forEach { sender ->
            val body = "شراء عبر الانترنت بمبلغ: 10.00 SAR"
            val raw = RawSms(
                id = "android-sms-live:$sender",
                sender = sender,
                body = body,
                receivedAt = Instant.parse("2026-08-03T08:00:00Z"),
                deviceMessageId = null,
                bodyHash = SmsBodyHasher.sha256Hex(body),
            )
            assertTrue(service.ingest(raw) is SmsIngestionResult.NotRelevant)
            assertNull(rawRepo.getById(raw.id))
        }
        assertEquals(0, db.rawSmsDao().count())
        assertEquals(0, parseCalls.get())
    }

    @Test
    fun parserFailure_keepsRawSmsAndReturnsFailed() = runBlocking {
        val exploding = SmsParseGateway { throw IllegalStateException("boom") }
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = exploding,
        )
        val raw = aljaziraPurchase(id = "android-sms:fail", deviceId = "fail")
        val result = svc.ingest(raw)
        assertTrue(result is SmsIngestionResult.Failed)
        assertEquals(raw, rawRepo.getById(raw.id))
        assertNull(parsedRepo.findByRawSmsId(raw.id))
    }

    @Test
    fun liveThenHistorical_sameEvidence_dedupes() = runBlocking {
        val receivedAt = Instant.parse("2026-08-01T12:26:00Z")
        val body = """
            عملية حوالة مالية صادرة مقبولة
            خصمت من حساب: 3002
            الى: TEST_BENEFICIARY
            مبلغ العملية: 13,258.00 SAR
            المعرف البديل \الايبان : 0593
            [البنك العربي الوطني]
            في: 2026-08-01 12:26
            رقم المعاملة: TEST_REFERENCE_1
        """.trimIndent()
        val live = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, "AlJazira", body, receivedAt),
        )
        val historical = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord("999", "AlJazira", body, receivedAt),
        )
        assertTrue(live.id != historical.id)
        assertEquals(live.bodyHash, historical.bodyHash)
        assertTrue(service.ingest(live) is SmsIngestionResult.Parsed)
        assertEquals(SmsIngestionResult.Duplicate, service.ingest(historical))
        assertEquals(1, db.rawSmsDao().count())
        assertEquals(1, parseCalls.get())
    }

    @Test
    fun multipartBodies_joinIntoOneRawSms() = runBlocking {
        val parts = listOf(
            "شراء عبر الانترنت\n",
            "بطاقة: 7271\n",
            "لدى: Keeta\n",
            "بمبلغ: 51.99 SAR\n",
            "في: 14:32 03-08-2026",
        )
        val combined = parts.joinToString("")
        val raw = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, "AlJazira", combined, Instant.parse("2026-08-03T14:32:00Z")),
        )
        assertEquals(combined, raw.body)
        val result = service.ingest(raw) as SmsIngestionResult.Parsed
        assertEquals(Money.of("51.99", Currency.SAR), result.event.amount)
        assertEquals("7271", result.event.cardRef?.last4)
        assertEquals(1, db.rawSmsDao().count())
    }

    private fun aljaziraPurchase(id: String, deviceId: String): RawSms {
        val body = """
            شراء عبر الانترنت
            بطاقة: 7271
            لدى: Keeta
            بمبلغ: 51.99 SAR
            في: 14:32 03-08-2026
            الرصيد المتاح: SAR 17230.03
        """.trimIndent()
        return RawSms(
            id = id,
            sender = "AlJazira",
            body = body,
            receivedAt = Instant.parse("2026-08-03T14:32:00Z"),
            deviceMessageId = deviceId,
            bodyHash = SmsBodyHasher.sha256Hex(body),
        )
    }
}
