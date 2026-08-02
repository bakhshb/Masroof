package com.baraa.masroof

import android.app.Application
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.repository.RoomCategoryRepository
import com.baraa.masroof.data.repository.RoomFinancialAccountRepository
import com.baraa.masroof.data.repository.RoomMerchantMemoryRepository
import com.baraa.masroof.data.repository.RoomTransactionRepository
import com.baraa.masroof.data.repository.TransactionImportService
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.sms.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Lazy-creates the Room database, all repositories,
 * the rule engine, and the import service. The default category list is
 * seeded on first launch.
 */
class MasroofApplication : Application() {

    val database: MasroofDatabase by lazy { MasroofDatabase.build(this) }

    val transactionRepository: TransactionRepository by lazy {
        RoomTransactionRepository(database.transactionDao())
    }

    val categoryRepository: RoomCategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao())
    }

    val merchantMemoryRepository: RoomMerchantMemoryRepository by lazy {
        RoomMerchantMemoryRepository(database.merchantMemoryDao())
    }

    val financialAccountRepository: RoomFinancialAccountRepository by lazy {
        RoomFinancialAccountRepository(database.financialAccountDao())
    }

    val smsRepository: SmsRepository by lazy { SmsRepository(this) }

    val importService: TransactionImportService by lazy {
        TransactionImportService(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            merchantMemoryRepository = merchantMemoryRepository,
            financialAccountRepository = financialAccountRepository,
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Seed the default category list exactly once. This runs after the
        // first DB creation (the Room callback invokes onCreate on the
        // database file) — we just check if any category exists and insert
        // the seed list if not.
        appScope.launch {
            runCatching { categoryRepository.seedIfEmpty() }
        }
    }
}
