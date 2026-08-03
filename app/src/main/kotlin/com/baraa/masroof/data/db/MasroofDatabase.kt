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
 * **Schema version 8**. v7 adds the financial-setup + opening-balance
 * foundation:
 *  - new columns on `financial_accounts` (accountNature, currency,
 *    openingBalance, openingBalanceDate, includeInNetWorth /
 *    includeInLiquidity, notes) — defaults are safe.
 *  - new table `financial_setup` (single-row.
 *    [FinancialSetupEntity.SINGLETON_ID]).
 *
 * The migration is **purely additive** — existing rows are preserved
 * and assigned conservative defaults. No
 * `fallbackToDestructiveMigration` is configured.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantMemoryEntity::class,
        FinancialAccountEntity::class,
        AiCacheEntity::class,
        AiSettingsEntity::class,
        AiSuggestionEntity::class,
        FinancialSetupEntity::class,
        JournalEntryEntity::class,
        LedgerPostingEntity::class,
    ],
    version = 8,
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
    abstract fun aiSuggestionDao(): AiSuggestionDao
    abstract fun financialSetupDao(): FinancialSetupDao
    abstract fun journalDao(): JournalDao

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

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_suggestions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `merchantDisplay` TEXT NOT NULL,
                        `amountBucket` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `promptVersion` TEXT NOT NULL,
                        `resultVersion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_suggestions_transactionId` ON `ai_suggestions`(`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_suggestions_status` ON `ai_suggestions`(`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_suggestions_createdAt` ON `ai_suggestions`(`createdAt`)")
            }
        }

        /**
         * v6 → v7: open the financial-accounts / financial-setup
         * foundation. Adds columns to `financial_accounts` and a new
         * `financial_setup` singleton table.
         *
         * Existing accounts get:
         *  - `accountNature` derived from the existing `accountType` via
         *    [com.baraa.masroof.transaction.AccountNature.defaultNatureFor]
         *  - `currency` = SAR (default currency)
         *  - `openingBalance` = 0
         *  - `openingBalanceDate` = 0 (the unsigned long sentinel — the
         *    UI interprets 0 as "no date" and shows the user an empty
         *    field)
         *  - `includeInNetWorth` = 1 (true)
         *  - `includeInLiquidity` = derived from the existing account
         *    type via [AccountLiquidityDefaults]
         *  - `notes` = NULL
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add columns with safe defaults; existing rows are
                // backfilled automatically.
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `accountNature` TEXT NOT NULL DEFAULT 'ASSET'"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'SAR'"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `openingBalance` TEXT NOT NULL DEFAULT '0'"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `openingBalanceDate` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `includeInNetWorth` INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `includeInLiquidity` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `financial_accounts` ADD COLUMN `notes` TEXT"
                )
                // Backfill accountNature from the existing accountType.
                // The defaults above set everything to ASSET / 0 / etc;
                // we now map per-type to the correct nature.
                db.execSQL("UPDATE `financial_accounts` SET `accountNature` = 'LIABILITY' WHERE `accountType` IN ('CREDIT_CARD', 'LOAN', 'OTHER_LIABILITY')")
                db.execSQL("UPDATE `financial_accounts` SET `accountNature` = 'ASSET' WHERE `accountType` NOT IN ('CREDIT_CARD', 'LOAN', 'OTHER_LIABILITY')")
                // Backfill includeInLiquidity from the existing accountType.
                db.execSQL("UPDATE `financial_accounts` SET `includeInLiquidity` = 1 WHERE `accountType` IN ('BANK_ACCOUNT', 'DIGITAL_WALLET', 'WALLET', 'CASH')")
                db.execSQL("UPDATE `financial_accounts` SET `includeInLiquidity` = 0 WHERE `accountType` NOT IN ('BANK_ACCOUNT', 'DIGITAL_WALLET', 'WALLET', 'CASH')")
                // Create the new financial_setup singleton table.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_setup` (
                        `id` INTEGER NOT NULL,
                        `trackingStartDate` INTEGER NOT NULL,
                        `setupCompleted` INTEGER NOT NULL,
                        `setupCompletedAt` INTEGER NOT NULL,
                        `defaultCurrency` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** v7 → v8 adds ledger tables and nullable account-link fields. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `financial_accounts` ADD COLUMN `systemAccountKey` TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_accounts_systemAccountKey` ON `financial_accounts`(`systemAccountKey`)")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `sourceAccountId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `destinationAccountId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `linkedJournalEntryId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountLinkSource` TEXT NOT NULL DEFAULT 'UNLINKED'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountLinkConfidence` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountLinkNeedsReview` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `postingStatus` TEXT NOT NULL DEFAULT 'UNPOSTED'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `journal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceTransactionId` INTEGER, `journalType` TEXT NOT NULL, `postingStatus` TEXT NOT NULL, `effectiveDate` TEXT NOT NULL, `effectiveTime` TEXT NOT NULL, `descriptionCode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `reversalOfJournalId` INTEGER, `notes` TEXT, `generatedBy` TEXT NOT NULL, `generationVersion` INTEGER NOT NULL, FOREIGN KEY(`sourceTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_sourceTransactionId` ON `journal_entries`(`sourceTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_postingStatus` ON `journal_entries`(`postingStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_effectiveDate_effectiveTime` ON `journal_entries`(`effectiveDate`, `effectiveTime`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ledger_postings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `journalEntryId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `postingSide` TEXT NOT NULL, `amount` TEXT NOT NULL, `currency` TEXT NOT NULL, `memoCode` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`journalEntryId`) REFERENCES `journal_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_postings_journalEntryId` ON `ledger_postings`(`journalEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_postings_accountId` ON `ledger_postings`(`accountId`)")
            }
        }

        /** All migrations in version order. New migrations go at the end. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
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
