package com.baraa.masroof

import android.app.Application
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.repository.RoomTransactionRepository
import com.baraa.masroof.data.repository.TransactionImportService
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.sms.SmsRepository

/**
 * Application entry point. Lazy-creates the Room database and the
 * repositories that need it so that the first screen render is not blocked
 * by a disk operation.
 */
class MasroofApplication : Application() {

    val database: MasroofDatabase by lazy { MasroofDatabase.build(this) }

    val transactionRepository: TransactionRepository by lazy {
        RoomTransactionRepository(database.transactionDao())
    }

    val smsRepository: SmsRepository by lazy { SmsRepository(this) }

    val importService: TransactionImportService by lazy {
        TransactionImportService(transactionRepository)
    }
}
