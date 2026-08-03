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
 * **Schema version 4**. v4 adds an `enabled` column to `merchant_memory`
 * so the user can disable a remembered merchant rule from the UI.
 *
 * The DB is **device-local only**: the application manifest disables cloud
 * backup and data extraction, no networking code touches this DB, and there
 * is no sync layer. **No destructive migration is configured** — every
 * schema bump ships with an explicit [Migration] that preserves existing
 * rows.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantMemoryEntity::class,
        FinancialAccountEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MasroofDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantMemoryDao(): MerchantMemoryDao
    abstract fun financialAccountDao(): FinancialAccountDao

    companion object {
        const val DATABASE_NAME: String = "masroof.db"

        /**
         * v1 → v2 migration: add the `transactionSimilarityKey` column.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` " +
                        "ADD COLUMN `transactionSimilarityKey` TEXT"
                )
            }
        }

        /**
         * v2 → v3 migration: add three new tables and seven new columns
         * to `transactions`. All new columns are nullable or have a
         * NOT NULL DEFAULT, so existing rows survive without any data
         * backfill.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // -- 1. New columns on transactions --
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `financialTreatment` TEXT NOT NULL DEFAULT 'PENDING_REVIEW'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categoryId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categorySource` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categoryConfidence` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `needsReview` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `userConfirmed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `exclusionReason` TEXT")

                // -- 2. New tables --
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parentId` INTEGER,
                        `nameAr` TEXT NOT NULL,
                        `nameEn` TEXT,
                        `sortOrder` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `isSystem` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`parentId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories`(`parentId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_nameAr_parentId` ON `categories`(`nameAr`, `parentId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_memory` (
                        `normalizedKey` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `preferredCategoryId` INTEGER,
                        `preferredFinancialTreatment` TEXT,
                        `confirmationCount` INTEGER NOT NULL DEFAULT 1,
                        `lastConfirmedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`normalizedKey`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `institutionName` TEXT,
                        `accountType` TEXT NOT NULL,
                        `lastFourDigits` TEXT,
                        `senderAliases` TEXT NOT NULL DEFAULT '',
                        `isOwnedByUser` INTEGER NOT NULL DEFAULT 1,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 → v4 migration: add the `enabled` column to `merchant_memory`.
         * Existing rows default to enabled=1; the UI can flip it to 0 to
         * disable a remembered rule.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `merchant_memory` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /** All migrations in version order. New migrations go at the end. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context): MasroofDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MasroofDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                // No fallbackToDestructiveMigration. If a future version is
                // published without a migration, Room will throw so tests
                // / QA catch it instead of silently deleting rows.
                .build()
    }
}
