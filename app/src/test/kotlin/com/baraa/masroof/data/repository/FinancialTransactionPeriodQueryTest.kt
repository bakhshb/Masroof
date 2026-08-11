package com.baraa.masroof.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FinancialTransactionPeriodQueryTest {
    private lateinit var db: MasroofDatabase
    private lateinit var repo: RoomFinancialTransactionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MasroofDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomFinancialTransactionRepository(db.financialTransactionDao(), db.parsedEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun listOccurredBetween_includesStartExcludesEndAndOrdersNewestFirst() = runBlocking {
        val start = Instant.parse("2026-07-27T00:00:00+03:00")
        val end = Instant.parse("2026-08-27T00:00:00+03:00")
        save(tx("before", start.minusSeconds(1)))
        save(tx("start", start))
        save(tx("mid-b", start.plusSeconds(20)))
        save(tx("mid-a", start.plusSeconds(10)))
        save(tx("end", end))
        save(tx("after", end.plusSeconds(1)))

        val result = repo.listOccurredBetween(start, end)
        assertEquals(listOf("mid-b", "mid-a", "start"), result.map { it.id })
    }

    private suspend fun save(tx: FinancialTransaction) {
        repo.save(tx, listOf("raw-${tx.id}"))
    }

    private fun tx(id: String, at: Instant): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = FinancialTransactionType.EXPENSE,
            amount = Money.of("10.00", Currency.SAR),
            occurredAt = at,
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = null,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )
}
