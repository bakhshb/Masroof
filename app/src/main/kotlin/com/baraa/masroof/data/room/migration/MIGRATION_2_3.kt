package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * P8: financial_transaction + financial_transaction_raw_sms_link.
 * Preserves raw_sms, parsed_event, and ownership registries.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `financial_transaction` (
              `id` TEXT NOT NULL,
              `type` TEXT NOT NULL,
              `amountDecimal` TEXT NOT NULL,
              `amountCurrency` TEXT NOT NULL,
              `occurredAtEpochMillis` INTEGER NOT NULL,
              `sourceContainerId` TEXT,
              `destinationContainerId` TEXT,
              `merchant` TEXT,
              `counterparty` TEXT,
              `categoryId` TEXT,
              PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_financial_transaction_occurredAtEpochMillis`
            ON `financial_transaction` (`occurredAtEpochMillis`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `financial_transaction_raw_sms_link` (
              `rawSmsId` TEXT NOT NULL,
              `transactionId` TEXT NOT NULL,
              PRIMARY KEY(`rawSmsId`),
              FOREIGN KEY(`rawSmsId`) REFERENCES `raw_sms`(`id`)
                ON UPDATE CASCADE ON DELETE RESTRICT,
              FOREIGN KEY(`transactionId`) REFERENCES `financial_transaction`(`id`)
                ON UPDATE CASCADE ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_financial_transaction_raw_sms_link_transactionId`
            ON `financial_transaction_raw_sms_link` (`transactionId`)
            """.trimIndent(),
        )
    }
}
