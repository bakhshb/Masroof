package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * P9: review_item + user_correction.
 * Preserves raw_sms, parsed_event, ownership registries, and financial transactions.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `review_item` (
              `id` TEXT NOT NULL,
              `rawSmsId` TEXT NOT NULL,
              `kind` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `reasons` TEXT NOT NULL,
              `createdAtEpochMillis` INTEGER NOT NULL,
              `updatedAtEpochMillis` INTEGER NOT NULL,
              `resolvedAtEpochMillis` INTEGER,
              `resolutionKind` TEXT,
              `resolvedTransactionId` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`rawSmsId`) REFERENCES `raw_sms`(`id`)
                ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            `index_review_item_rawSmsId`
            ON `review_item` (`rawSmsId`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_review_item_status`
            ON `review_item` (`status`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_correction` (
              `id` TEXT NOT NULL,
              `targetRawSmsId` TEXT NOT NULL,
              `correctedMessageFamily` TEXT,
              `correctedAmountDecimal` TEXT,
              `correctedAmountCurrency` TEXT,
              `correctedMerchant` TEXT,
              `correctedCounterparty` TEXT,
              `createdAtEpochMillis` INTEGER NOT NULL,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`targetRawSmsId`) REFERENCES `raw_sms`(`id`)
                ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            `index_user_correction_targetRawSmsId_createdAtEpochMillis`
            ON `user_correction` (`targetRawSmsId`, `createdAtEpochMillis`)
            """.trimIndent(),
        )
    }
}
