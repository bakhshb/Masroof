package com.baraa.masroof.accounts

import com.baraa.masroof.data.repository.FinancialSetup
import com.baraa.masroof.data.repository.FinancialSetupRepository
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory [FinancialSetupRepository] for unit tests. Stores the
 * single setup record in a thread-safe holder. Mirrors the production
 * contract: a missing row yields the default.
 */
class FakeFinancialSetupRepository(
    initial: FinancialSetup = FinancialSetup.defaultFor(today = 1_700_000_000_000L)
) : FinancialSetupRepository {

    private val ref = AtomicReference(initial)
    private val flow = MutableStateFlow(initial)

    override suspend fun load(): FinancialSetup = ref.get()

    override suspend fun save(setup: FinancialSetup) {
        ref.set(setup)
        flow.value = setup
    }

    /** Observable form for views that want to react to changes. */
    override fun observe(): Flow<FinancialSetup> = flow.map { it }
}
