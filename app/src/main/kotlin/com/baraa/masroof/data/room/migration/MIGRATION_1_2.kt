package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * P7: add account_registry and card_registry. Preserves raw_sms and parsed_event.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `account_registry` (
              `bankId` TEXT NOT NULL,
              `maskedNumber` TEXT NOT NULL,
              `ownershipStatus` TEXT NOT NULL,
              `firstSeenRawSmsId` TEXT,
              `lastSeenRawSmsId` TEXT,
              PRIMARY KEY(`bankId`, `maskedNumber`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            `index_account_registry_bankId_maskedNumber`
            ON `account_registry` (`bankId`, `maskedNumber`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `card_registry` (
              `bankId` TEXT NOT NULL,
              `last4` TEXT NOT NULL,
              `ownershipStatus` TEXT NOT NULL,
              `firstSeenRawSmsId` TEXT,
              `lastSeenRawSmsId` TEXT,
              PRIMARY KEY(`bankId`, `last4`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            `index_card_registry_bankId_last4`
            ON `card_registry` (`bankId`, `last4`)
            """.trimIndent(),
        )
    }
}
