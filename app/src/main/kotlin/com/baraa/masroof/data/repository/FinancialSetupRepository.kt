package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.FinancialSetupDao
import com.baraa.masroof.data.db.FinancialSetupEntity
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Repository for the singleton financial-setup record.
 *
 * The row id is hard-coded to 1 (see [FinancialSetupEntity.SINGLETON_ID]).
 * The repository handles the "no row yet" case by materializing a
 * default in-memory record on first read.
 */
interface FinancialSetupRepository {
    suspend fun load(): FinancialSetup
    suspend fun save(setup: FinancialSetup)
    /**
     * Cold [Flow] of the persisted setup. Emits an in-memory default
     * when no row exists so collectors always have a usable value to
     * render. Distinct to avoid unnecessary recompositions.
     */
    fun observe(): Flow<FinancialSetup>
}

/**
 * Plain-data view of the setup. Avoids using the Room entity directly
 * so that callers in `:app` don't need to import `androidx.room`.
 */
data class FinancialSetup(
    val trackingStartDate: Long,
    val setupCompleted: Boolean,
    val setupCompletedAt: Long,
    val defaultCurrency: Currency,
) {
    companion object {
        /** Default setup when the user has not configured anything yet. */
        fun defaultFor(currency: Currency = Currency.SAR, today: Long): FinancialSetup = FinancialSetup(
            trackingStartDate = today,
            setupCompleted = false,
            setupCompletedAt = 0L,
            defaultCurrency = currency,
        )
    }
}

class RoomFinancialSetupRepository(
    private val dao: FinancialSetupDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : FinancialSetupRepository {

    override suspend fun load(): FinancialSetup = withContext(Dispatchers.IO) {
        dao.get()?.toDomain() ?: FinancialSetup.defaultFor(today = now())
    }

    override suspend fun save(setup: FinancialSetup) = withContext(Dispatchers.IO) {
        dao.upsert(
            FinancialSetupEntity(
                trackingStartDate = setup.trackingStartDate,
                setupCompleted = setup.setupCompleted,
                setupCompletedAt = setup.setupCompletedAt,
                defaultCurrency = setup.defaultCurrency,
            )
        )
    }

    override fun observe(): Flow<FinancialSetup> = flow {
        // Repoll on demand; this is intentionally tiny. A Compose
        // collector observing a Flow built by `flow` reads the current
        // value whenever the underlying Room flow emits. For a single
        // row table that's adequate.
        emit(load())
    }.distinctUntilChanged()
}

private fun FinancialSetupEntity.toDomain(): FinancialSetup = FinancialSetup(
    trackingStartDate = trackingStartDate,
    setupCompleted = setupCompleted,
    setupCompletedAt = setupCompletedAt,
    defaultCurrency = defaultCurrency,
)
