package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Commitment pause/resume history intervals.
 */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `commitment`
            ADD COLUMN `pauseIntervalsJson` TEXT NOT NULL DEFAULT '[]'
            """.trimIndent(),
        )
    }
}
