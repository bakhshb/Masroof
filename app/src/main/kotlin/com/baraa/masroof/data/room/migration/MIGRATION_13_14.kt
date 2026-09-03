package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Backfill open pause intervals for inactive commitments that predate stored pause history.
 * Touches the commitment table only.
 */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `commitment`
            SET `pauseIntervalsJson` = '[{"pausedAtEpochMillis":' || `updatedAtEpochMillis` || '}]'
            WHERE `active` = 0
              AND (`pauseIntervalsJson` = '[]' OR `pauseIntervalsJson` = '')
            """.trimIndent(),
        )
    }
}
