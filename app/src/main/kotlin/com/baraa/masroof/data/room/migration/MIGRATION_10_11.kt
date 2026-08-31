package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Parsed-event bank-neutral facts: loan type, debit source account, salary wording.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN loanType TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN debitSourceAccountLast4 TEXT")
        db.execSQL("ALTER TABLE parsed_event ADD COLUMN salaryIncomeWording INTEGER")
    }
}
