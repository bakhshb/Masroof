package com.baraa.masroof.presentation.review

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository
import com.baraa.masroof.application.review.EffectiveParsedEventProvider
import com.baraa.masroof.application.review.ReviewDetailLoader
import com.baraa.masroof.application.review.ReviewListDataSource
import com.baraa.masroof.application.review.ReviewQueueUpdater
import com.baraa.masroof.application.review.ReviewWorkflowService
import com.baraa.masroof.application.transaction.TransactionReconciliationService
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomManualReviewResolutionRepository
import com.baraa.masroof.data.repository.RoomParsedEventRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.repository.RoomReviewRepository
import com.baraa.masroof.data.repository.RoomUserCorrectionRepository
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.ReviewItem
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.ReviewResolutionKind
import com.baraa.masroof.domain.model.ReviewStatus
import com.baraa.masroof.domain.ownership.OwnershipConfirmationService
import com.baraa.masroof.domain.ownership.OwnershipResolver
import com.baraa.masroof.sms.time.InstantClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: MasroofDatabase
    private lateinit var detailLoader: ReviewDetailLoader
    private lateinit var workflow: ReviewWorkflowService
    private val clock = InstantClock { Instant.parse("2026-08-11T12:00:00Z") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val rawRepo = RoomRawSmsRepository(db.rawSmsDao())
        val parsedRepo = RoomParsedEventRepository(db.parsedEventDao())
        val ftRepo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
        val reviewRepo = RoomReviewRepository(db.reviewItemDao())
        val correctionRepo = RoomUserCorrectionRepository(db.userCorrectionDao())
        val accounts = RoomAccountRegistryRepository(db.accountRegistryDao())
        val cards = RoomCardRegistryRepository(db.cardRegistryDao())
        val ownershipResolver = OwnershipResolver(accounts, cards)
        val effective = EffectiveParsedEventProvider(parsedRepo, correctionRepo)
        val reconciliation = TransactionReconciliationService(
            parsedEventRepository = parsedRepo,
            rawSmsRepository = rawRepo,
            financialTransactionRepository = ftRepo,
            ownershipResolver = ownershipResolver,
            effectiveParsedEventProvider = effective,
        )
        workflow = ReviewWorkflowService(
            reviewRepository = reviewRepo,
            userCorrectionRepository = correctionRepo,
            financialTransactionRepository = ftRepo,
            rawSmsRepository = rawRepo,
            ownershipResolver = ownershipResolver,
            effectiveParsedEventProvider = effective,
            reconciliationService = reconciliation,
            reviewQueueUpdater = ReviewQueueUpdater(reviewRepo, ftRepo, clock),
            manualReviewResolutionRepository = RoomManualReviewResolutionRepository(db, ftRepo),
            clock = clock,
        )
        detailLoader = ReviewDetailLoader(
            reviewWorkflowService = workflow,
            rawSmsRepository = rawRepo,
            effectiveParsedEventProvider = effective,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun setListMode_ignored_loadsIgnoredSummaries() = runTest {
        val ignoredCalls = AtomicInteger(0)
        val vm = ReviewViewModel(
            reviewWorkflowService = workflow,
            listDataSource = object : ReviewListDataSource {
                override suspend fun loadPendingSummaries(): List<ReviewDetailLoader.ReviewSummary> =
                    listOf(summary("pending"))

                override suspend fun loadIgnoredSummaries(): List<ReviewDetailLoader.ReviewSummary> {
                    ignoredCalls.incrementAndGet()
                    return listOf(summary("ignored"))
                }
            },
            detailLoader = detailLoader,
            cardRegistryRepository = RoomCardRegistryRepository(db.cardRegistryDao()),
            ownershipConfirmationService = OwnershipConfirmationService(
                accountRegistry = RoomAccountRegistryRepository(db.accountRegistryDao()),
                cardRegistry = RoomCardRegistryRepository(db.cardRegistryDao()),
            ),
            refreshReviewQueue = {},
            reparseStoredSms = {},
            appLocaleRepository = FakeAppLocaleRepository(),
        )
        vm.refresh()
        advanceUntilIdle()
        assertEquals(listOf("pending"), vm.uiState.value.items.map { it.id })

        vm.setListMode(ReviewListMode.IGNORED)
        assertEquals(ReviewListMode.IGNORED, vm.uiState.value.listMode)
        assertTrue(vm.uiState.value.loading)
        assertEquals(listOf("pending"), vm.uiState.value.items.map { it.id })

        advanceUntilIdle()
        assertEquals(1, ignoredCalls.get())
        assertEquals(listOf("ignored"), vm.uiState.value.items.map { it.id })
        assertFalse(vm.uiState.value.loading)
    }

    private fun summary(id: String): ReviewDetailLoader.ReviewSummary =
        ReviewDetailLoader.ReviewSummary(
            review = ReviewItem(
                id = id,
                rawSmsId = "sms-$id",
                kind = ReviewKind.NEEDS_REVIEW,
                status = ReviewStatus.RESOLVED,
                reasons = listOf("user_ignored_transaction"),
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
                resolvedAt = Instant.parse("2026-08-02T00:00:00Z"),
                resolutionKind = ReviewResolutionKind.USER_NON_FINANCIAL,
                resolvedTransactionId = null,
            ),
            title = "Sample",
            amount = Money.of("10.00", Currency.SAR),
            messageFamily = null,
            receivedAt = Instant.parse("2026-08-01T00:00:00Z"),
            body = "body",
        )

    private class FakeAppLocaleRepository : AppLocaleRepository {
        override fun getLanguageTag(): String = AppLocale.DEFAULT_TAG

        override fun setLanguageTag(languageTag: String) = Unit
    }
}
