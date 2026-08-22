package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Registry metadata: display names, card network/type, Mada account link, credit facility roles.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `account_registry`
            ADD COLUMN `displayName` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `displayName` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `cardNetwork` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `cardType` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `linkedAccountBankId` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `linkedAccountMaskedNumber` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `parentCardLast4` TEXT
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `cardRole` TEXT
            """.trimIndent(),
        )
    }
}
