package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Parsed-event dashboard facts: card SMS channel, payment due date, and international purchase fields.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN cardSmsChannel TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN paymentDueDate TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN exchangeRate TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN internationalFeeDecimal TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN internationalFeeCurrency TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN labeledForeignAmountDecimal TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN labeledForeignAmountCurrency TEXT")
    }
}
