package com.baraa.masroof.sms.ingestion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.review.ReviewQueueUpdater
import com.baraa.masroof.application.transaction.TransactionReconciliationService
import com.baraa.masroof.bank.aljazira.AlJaziraBankDetector
import com.baraa.masroof.bank.aljazira.AlJaziraParsingPipeline
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.domain.repository.FinancialTransactionRepository
import com.baraa.masroof.domain.repository.FinancialTransactionSaveResult
import com.baraa.masroof.domain.repository.ReviewRepository
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.parser.SmsParseGateway
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import com.baraa.masroof.parsing.repository.ParsedEventRepository
import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.sms.mapper.AndroidSmsMapper
import com.baraa.masroof.sms.model.ProviderSmsRecord
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun parserCancellation_propagates() = runBlocking {
        val cancelling = SmsParseGateway { throw CancellationException("cancel") }
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = cancelling,
        )
        val raw = aljaziraPurchase(id = "android-sms:cancel", deviceId = "cancel")
        try {
            svc.ingest(raw)
            org.junit.Assert.fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(raw, rawRepo.getById(raw.id))
    }

    @Test
    fun parsedEventSaveFailure_returnsFailed_keepsRawSms() = runBlocking {
        val failingParsedRepo = object : ParsedEventRepository {
            override suspend fun save(event: ParsedEvent, details: ParsedEventDetails) {
                throw IllegalStateException("save failed")
            }

            override suspend fun getById(id: String): ParsedEventRecord? = null
            override suspend fun findByRawSmsId(rawSmsId: String): ParsedEventRecord? = null
            override suspend fun deleteByRawSmsId(rawSmsId: String) = Unit
            override suspend fun listAll(): List<ParsedEventRecord> = emptyList()
        }
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = failingParsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
        )
        val raw = aljaziraPurchase(id = "android-sms:save-fail", deviceId = "save-fail")
        val result = svc.ingest(raw)
        assertTrue(result is SmsIngestionResult.Failed)
        assertEquals(raw, rawRepo.getById(raw.id))
    }

    @Test
    fun liveThenHistorical_withSlightlyDifferentMillis_dedupes() = runBlocking {
        val body = transferBody()
        val liveAt = Instant.parse("2026-08-10T12:00:00.000Z")
        val historicalAt = Instant.parse("2026-08-10T12:00:02.000Z")
        val live = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, "AlJazira", body, liveAt),
        )
        val historical = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord("999", "AlJazira", body, historicalAt),
        )
        assertTrue(live.id != historical.id)
        assertEquals(live.bodyHash, historical.bodyHash)
        assertTrue(service.ingest(live) is SmsIngestionResult.Parsed)
        assertEquals(SmsIngestionResult.Duplicate, service.ingest(historical))
        assertEquals(1, db.rawSmsDao().count())
        assertEquals(1, parseCalls.get())
    }

    @Test
    fun twoHistoricalNearby_sameBody_areNotCollapsedByTolerance() = runBlocking {
        val body = transferBody()
        val a = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord("10", "AlJazira", body, Instant.parse("2026-08-10T12:00:00Z")),
        )
        val b = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord("11", "AlJazira", body, Instant.parse("2026-08-10T12:00:02Z")),
        )
        assertTrue(service.ingest(a) is SmsIngestionResult.Parsed)
        assertTrue(service.ingest(b) is SmsIngestionResult.Parsed)
        assertEquals(2, db.rawSmsDao().count())
        assertEquals(2, parseCalls.get())
    }

    @Test
    fun twoLiveNearby_sameBody_areNotCollapsedByCrossSourceTolerance() = runBlocking {
        val body = transferBody()
        val a = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, "AlJazira", body, Instant.parse("2026-08-10T12:00:00Z")),
        )
        val b = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, "AlJazira", body, Instant.parse("2026-08-10T12:00:02Z")),
        )
        assertTrue(service.ingest(a) is SmsIngestionResult.Parsed)
        assertTrue(service.ingest(b) is SmsIngestionResult.Parsed)
        assertEquals(2, db.rawSmsDao().count())
    }

    @Test
    fun liveReceivedAt_usesReceiptClockNotSmsc() {
        val fixed = Instant.parse("2026-08-10T15:30:00Z")
        val clock = InstantClock { fixed }
        assertEquals(fixed, clock.now())
        // IncomingSmsReceiver uses AppContainer.clock; assembler does not touch SMSC time.
        val assembled = com.baraa.masroof.sms.receiver.ReceivedSmsAssembler.assemble(
            listOf(
                com.baraa.masroof.sms.receiver.ReceivedSmsAssembler.Part("AlJazira", "body"),
            ),
        )!!
        val raw = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(null, assembled.sender, assembled.body, clock.now()),
        )
        assertEquals(fixed, raw.receivedAt)
    }

    @Test
    fun p8DerivedSaveException_keepsSuccessfulEvidenceAndDoesNotFailIngest() = runBlocking {
        val throwingFtRepo = object : FinancialTransactionRepository {
            override suspend fun save(
                transaction: FinancialTransaction,
                rawSmsIds: Collection<String>,
            ): FinancialTransactionSaveResult {
                throw IllegalStateException("p8-save-boom")
            }

            override suspend fun getById(id: String): FinancialTransaction? = null
            override suspend fun findByRawSmsId(rawSmsId: String): FinancialTransaction? = null
            override suspend fun listAll(): List<FinancialTransaction> = emptyList()
            override suspend fun listOccurredBetween(
                startInclusive: java.time.Instant,
                endExclusive: java.time.Instant,
            ): List<FinancialTransaction> = emptyList()
            override suspend fun isRawSmsLinked(rawSmsId: String): Boolean = false
            override suspend fun listRawSmsIds(transactionId: String): List<String> = emptyList()
            override suspend fun update(transaction: FinancialTransaction): Boolean = false
            override suspend fun deleteIfExclusiveRawSmsLink(rawSmsId: String): Boolean = false
        }
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = throwingFtRepo,
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
            reconciliation = reconciliation,
        )
        val raw = aljaziraPurchase(id = "android-sms:p8-fail", deviceId = "p8-fail")
        val result = svc.ingest(raw)
        assertTrue(result is SmsIngestionResult.Parsed)
        assertNotNull(rawRepo.getById(raw.id))
        assertNotNull(parsedRepo.findByRawSmsId(raw.id))
        assertEquals(MessageFamily.PURCHASE, parsedRepo.findByRawSmsId(raw.id)!!.event.messageFamily)
    }

    @Test
    fun p9ReviewQueueException_keepsEvidenceAndDoesNotFailIngest() = runBlocking {
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        val ftRepo = RoomFinancialTransactionRepository(
            db.financialTransactionDao(),
            db.parsedEventDao(),
        )
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
        val throwingReviews = object : ReviewRepository {
            override suspend fun getById(id: String): ReviewItem? = null
            override suspend fun findByRawSmsId(rawSmsId: String): ReviewItem? = null
            override suspend fun listRequired(): List<ReviewItem> = emptyList()
            override suspend fun listAll(): List<ReviewItem> = emptyList()
            override suspend fun upsertRequired(
                rawSmsId: String,
                kind: ReviewKind,
                reasons: List<String>,
                now: Instant,
            ): ReviewItem {
                throw IllegalStateException("p9-review-boom")
            }

            override suspend fun markResolved(
                id: String,
                resolutionKind: ReviewResolutionKind,
                resolvedAt: Instant,
                resolvedTransactionId: String?,
            ): ReviewItem? = null
        }
        val updater = ReviewQueueUpdater(
            reviewRepository = throwingReviews,
            financialTransactionRepository = ftRepo,
            clock = InstantClock.System,
        )
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
            reconciliation = reconciliation,
            reviewQueueUpdater = updater,
        )
        val raw = aljaziraBillPayment(id = "android-sms:p9-fail", deviceId = "p9-fail")
        val result = svc.ingest(raw)
        assertTrue(result is SmsIngestionResult.Parsed)
        assertNotNull(rawRepo.getById(raw.id))
        assertNotNull(parsedRepo.findByRawSmsId(raw.id))
        assertEquals(MessageFamily.BILL_PAYMENT, parsedRepo.findByRawSmsId(raw.id)!!.event.messageFamily)
        // Bill payment stays NeedsReview — no FT required for this boundary.
        assertTrue(ftRepo.listAll().isEmpty() || ftRepo.findByRawSmsId(raw.id) != null)
    }

    @Test
    fun p9ReviewCancellation_propagatesOutOfIngestion() = runBlocking {
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        val ftRepo = RoomFinancialTransactionRepository(
            db.financialTransactionDao(),
            db.parsedEventDao(),
        )
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
        val cancellingReviews = object : ReviewRepository {
            override suspend fun getById(id: String): ReviewItem? = null
            override suspend fun findByRawSmsId(rawSmsId: String): ReviewItem? = null
            override suspend fun listRequired(): List<ReviewItem> = emptyList()
            override suspend fun listAll(): List<ReviewItem> = emptyList()
            override suspend fun upsertRequired(
                rawSmsId: String,
                kind: ReviewKind,
                reasons: List<String>,
                now: Instant,
            ): ReviewItem {
                throw CancellationException("p9-cancel")
            }

            override suspend fun markResolved(
                id: String,
                resolutionKind: ReviewResolutionKind,
                resolvedAt: Instant,
                resolvedTransactionId: String?,
            ): ReviewItem? = null
        }
        val updater = ReviewQueueUpdater(
            reviewRepository = cancellingReviews,
            financialTransactionRepository = ftRepo,
            clock = InstantClock.System,
        )
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
            reconciliation = reconciliation,
            reviewQueueUpdater = updater,
        )
        val raw = aljaziraBillPayment(id = "android-sms:p9-cancel", deviceId = "p9-cancel")
        try {
            svc.ingest(raw)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
        assertNotNull(rawRepo.getById(raw.id))
        assertNotNull(parsedRepo.findByRawSmsId(raw.id))
    }

    @Test
    fun p8DerivedCancellation_propagatesOutOfIngestion() = runBlocking {
        val cancellingFtRepo = object : FinancialTransactionRepository {
            override suspend fun save(
                transaction: FinancialTransaction,
                rawSmsIds: Collection<String>,
            ): FinancialTransactionSaveResult {
                throw CancellationException("p8-cancel")
            }

            override suspend fun getById(id: String): FinancialTransaction? = null
            override suspend fun findByRawSmsId(rawSmsId: String): FinancialTransaction? = null
            override suspend fun listAll(): List<FinancialTransaction> = emptyList()
            override suspend fun listOccurredBetween(
                startInclusive: java.time.Instant,
                endExclusive: java.time.Instant,
            ): List<FinancialTransaction> = emptyList()
            override suspend fun isRawSmsLinked(rawSmsId: String): Boolean = false
            override suspend fun listRawSmsIds(transactionId: String): List<String> = emptyList()
            override suspend fun update(transaction: FinancialTransaction): Boolean = false
            override suspend fun deleteIfExclusiveRawSmsLink(rawSmsId: String): Boolean = false
        }
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = cancellingFtRepo,
            ownershipResolver = OwnershipResolver(accounts, cards),
        )
        val svc = SmsIngestionService(
            rawSmsRepository = rawRepo,
            parsedEventRepository = parsedRepo,
            bankDetector = AlJaziraBankDetector(),
            parseGateway = AlJaziraParsingPipeline(),
            reconciliation = reconciliation,
        )
        val raw = aljaziraPurchase(id = "android-sms:p8-cancel", deviceId = "p8-cancel")
        try {
            svc.ingest(raw)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected — must not become SmsIngestionResult.Failed
        }
        // Evidence persisted before cancellation remains.
        assertNotNull(rawRepo.getById(raw.id))
        assertNotNull(parsedRepo.findByRawSmsId(raw.id))
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

    private fun aljaziraBillPayment(id: String, deviceId: String): RawSms {
        val body = """
            سداد فاتورة
            المفوتر: TEST_BILLER
            بمبلغ: 210.00 SAR
            من حساب: 3001
            في: 2026-08-03 16:40
        """.trimIndent()
        return RawSms(
            id = id,
            sender = "AlJazira",
            body = body,
            receivedAt = Instant.parse("2026-08-03T16:40:00Z"),
            deviceMessageId = deviceId,
            bodyHash = SmsBodyHasher.sha256Hex(body),
        )
    }

    private fun transferBody() = """
        عملية حوالة مالية صادرة مقبولة
        خصمت من حساب: 3002
        الى: TEST_BENEFICIARY
        مبلغ العملية: 13,258.00 SAR
        المعرف البديل \الايبان : 0593
        [البنك العربي الوطني]
        في: 2026-08-01 12:26
        رقم المعاملة: TEST_REFERENCE_1
    """.trimIndent()
}
