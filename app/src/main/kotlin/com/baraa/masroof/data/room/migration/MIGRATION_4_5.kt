package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Persist applied USD→SAR exchange rate and its provenance on financial transactions.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `financial_transaction`
            ADD COLUMN `appliedExchangeRate` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `financial_transaction`
            ADD COLUMN `exchangeRateSource` TEXT
            """.trimIndent(),
        )
    }
}
