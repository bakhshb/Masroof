package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * User-defined commitments linked to source transactions.
 */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `commitment` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `amountDecimal` TEXT NOT NULL,
                `amountCurrency` TEXT NOT NULL,
                `transactionDateIso` TEXT NOT NULL,
                `recurrence` TEXT,
                `dueDateIso` TEXT,
                `active` INTEGER NOT NULL,
                `sourceTransactionId` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_commitment_sourceTransactionId`
            ON `commitment` (`sourceTransactionId`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_commitment_active`
            ON `commitment` (`active`)
            """.trimIndent(),
        )
    }
}
