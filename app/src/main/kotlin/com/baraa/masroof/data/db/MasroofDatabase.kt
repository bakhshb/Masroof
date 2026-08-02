package com.baraa.masroof.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local Room database for the Masroof app.
 *
 * **Schema version 2** (single table `transactions`). v2 adds the
 * `transactionSimilarityKey` column used for near-duplicate detection.
 *
 * The DB is **device-local only**: the application manifest disables cloud
 * backup and data extraction, no networking code touches this DB, and there
 * is no sync layer. **No destructive migration is configured** — on a schema
 * bump, [MIGRATION_1_2] is applied explicitly so existing transactions
 * survive an app upgrade.
 */
@Database(
    entities = [TransactionEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MasroofDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DATABASE_NAME: String = "masroof.db"

        /**
         * v1 → v2 migration: add the `transactionSimilarityKey` column.
         *
         * Existing rows are left with NULL; the import service will
         * backfill similarity keys lazily as messages are re-imported, or
         * the next time a duplicate is checked. NULL is treated as "no
         * key yet" by the duplicate-detection logic, so it is safe to
         * leave NULL.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` " +
                        "ADD COLUMN `transactionSimilarityKey` TEXT"
                )
            }
        }

        /** All migrations in version order. New migrations go at the end. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): MasroofDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MasroofDatabase::class.java,
                DATABASE_NAME,
            )
                // Explicit migrations only. We never silently wipe the DB.
                .addMigrations(*ALL_MIGRATIONS)
                // Defense-in-depth: if a future schema version is published
                // without a corresponding migration, Room will THROW (so
                // tests / QA catch it) instead of silently deleting rows.
                .build()
    }
}
