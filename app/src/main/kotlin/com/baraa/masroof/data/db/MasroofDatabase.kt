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
 * **Schema version 5**. v5 adds:
 *  - `ai_cache` table — sanitized AI suggestion cache (no raw prompts,
 *    no raw responses, no API key, no merchant names beyond the
 *    normalized key, no exact amounts)
 *  - `ai_settings` table — AI provider settings (the API key itself is
 *    stored in Keystore-backed encrypted storage, never in Room)
 *
 * The DB is **device-local only**. No destructive migration is
 * configured. Every schema bump ships with an explicit [Migration].
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantMemoryEntity::class,
        FinancialAccountEntity::class,
        AiCacheEntity::class,
        AiSettingsEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MasroofDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantMemoryDao(): MerchantMemoryDao
    abstract fun financialAccountDao(): FinancialAccountDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun aiSettingsDao(): AiSettingsDao

    companion object {
        const val DATABASE_NAME: String = "masroof.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` " +
                        "ADD COLUMN `transactionSimilarityKey` TEXT"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `financialTreatment` TEXT NOT NULL DEFAULT 'PENDING_REVIEW'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categoryId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categorySource` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categoryConfidence` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `needsReview` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `userConfirmed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `exclusionReason` TEXT")

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

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `merchant_memory` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * v4 → v5 migration: add `ai_cache` and `ai_settings` tables.
         * No existing rows change. No API key is ever persisted in either
         * of these tables — the key lives in Keystore-backed encrypted
         * shared preferences.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_cache` (
                        `normalizedMerchantKey` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `promptVersion` TEXT NOT NULL,
                        `resultVersion` TEXT NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `userAccepted` INTEGER NOT NULL DEFAULT 0,
                        `userRejected` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`normalizedMerchantKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_cache_normalizedMerchantKey` ON `ai_cache`(`normalizedMerchantKey`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_settings` (
                        `id` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `providerLabel` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `shareExactAmount` INTEGER NOT NULL,
                        `minimumConfidence` INTEGER NOT NULL,
                        `requireHttps` INTEGER NOT NULL,
                        `timeoutMillis` INTEGER NOT NULL,
                        `hasApiKey` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** All migrations in version order. New migrations go at the end. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )

        fun build(context: Context): MasroofDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MasroofDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}