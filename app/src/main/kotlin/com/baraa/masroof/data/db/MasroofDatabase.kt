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
        AccountLinkRuleEntity::class,
        AccountIdentifierEntity::class,
        SenderInstitutionMappingEntity::class,
        TransactionSmsBodyEntity::class,
        SenderMessagePatternEntity::class,
        SenderProfileEntity::class,
        AccountSenderProfileCrossRef::class,
        MessagePatternDefinitionEntity::class,
        PatternFieldDefinitionEntity::class,
    ],
    version = 21,
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
    abstract fun accountLinkRuleDao(): AccountLinkRuleDao
    abstract fun accountIdentifierDao(): AccountIdentifierDao
    abstract fun senderInstitutionMappingDao(): SenderInstitutionMappingDao
    abstract fun transactionSmsBodyDao(): TransactionSmsBodyDao
    abstract fun senderMessagePatternDao(): SenderMessagePatternDao
    abstract fun senderProfileDao(): SenderProfileDao
    abstract fun accountSenderProfileDao(): AccountSenderProfileDao
    abstract fun messagePatternDefinitionDao(): MessagePatternDefinitionDao
    abstract fun patternFieldDefinitionDao(): PatternFieldDefinitionDao

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

        /** v8 → v9 stores safe, user-confirmed account-link signatures. */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `account_link_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `signature` TEXT NOT NULL, `senderKey` TEXT NOT NULL, `institutionKey` TEXT, `parserName` TEXT NOT NULL, `transactionType` TEXT NOT NULL, `financialTreatment` TEXT NOT NULL, `channel` TEXT NOT NULL, `direction` TEXT NOT NULL, `expectedAccountType` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `confirmationCount` INTEGER NOT NULL, `lastConfirmedAt` INTEGER NOT NULL, `active` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_account_link_rules_signature` ON `account_link_rules` (`signature`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_link_rules_accountId` ON `account_link_rules` (`accountId`)")
            }
        }

        /** v9 → v10 adds lastUsedAt for learned linking UI. */
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `account_link_rules` ADD COLUMN `lastUsedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v10 → v11 introduces account_identifiers with safe per-account last-four support. */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `account_identifiers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `identifierType` TEXT NOT NULL, `normalizedValue` TEXT NOT NULL, `displayLabel` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_account_identifiers_normalizedValue` ON `account_identifiers` (`normalizedValue`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_identifiers_accountId` ON `account_identifiers` (`accountId`)")
            }
        }

        /**
         * v11 → v12 adds the `sender_institution_mapping` table for
         * institution-aware SMS identification. Purely additive.
         */
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sender_institution_mapping` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderKey` TEXT NOT NULL, `institutionName` TEXT NOT NULL, `isActive` INTEGER NOT NULL DEFAULT 1, `confirmationCount` INTEGER NOT NULL DEFAULT 1, `lastConfirmedAt` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sender_institution_mapping_senderKey` ON `sender_institution_mapping` (`senderKey`)")
            }
        }

        /**
         * v12 → v13 adds credit-card credit limit + opening-balance kind
         * to `financial_accounts`. Default values keep existing rows valid.
         */
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `financial_accounts` ADD COLUMN `creditLimit` TEXT")
                db.execSQL("ALTER TABLE `financial_accounts` ADD COLUMN `openingBalanceKind` TEXT NOT NULL DEFAULT 'OUTSTANDING'")
            }
        }

        /**
         * v13 → v14 permits one sender alias to belong to multiple accounts.
         * The table rebuild is SQLite's safe way to replace the former global
         * normalizedValue uniqueness constraint; every row is copied verbatim.
         */
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE `account_identifiers_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `accountId` INTEGER NOT NULL,
                        `identifierType` TEXT NOT NULL,
                        `normalizedValue` TEXT NOT NULL,
                        `displayLabel` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""INSERT INTO `account_identifiers_new`
                    (`id`,`accountId`,`identifierType`,`normalizedValue`,`displayLabel`,`isActive`,`createdAt`,`updatedAt`)
                    SELECT `id`,`accountId`,`identifierType`,`normalizedValue`,`displayLabel`,`isActive`,`createdAt`,`updatedAt`
                    FROM `account_identifiers`""".trimIndent())
                db.execSQL("DROP TABLE `account_identifiers`")
                db.execSQL("ALTER TABLE `account_identifiers_new` RENAME TO `account_identifiers`")
                db.execSQL("CREATE UNIQUE INDEX `index_account_identifiers_accountId_identifierType_normalizedValue` ON `account_identifiers` (`accountId`, `identifierType`, `normalizedValue`)")
                db.execSQL("CREATE INDEX `index_account_identifiers_accountId` ON `account_identifiers` (`accountId`)")
            }
        }

        /**
         * v14 → v15 retires legacy `lastFourDigits` / `senderAliases` columns
         * from `financial_accounts` after copying them into typed
         * `account_identifiers` rows. Account rows, transactions, and journals
         * are preserved; the table rebuild copies every non-legacy column.
         */
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                // 1) Backfill typed identifiers from legacy columns (idempotent).
                db.query(
                    """
                    SELECT `id`, `accountType`, `lastFourDigits`, `senderAliases`, `createdAt`, `updatedAt`, `systemAccountKey`
                    FROM `financial_accounts`
                    """.trimIndent(),
                ).use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val typeIdx = cursor.getColumnIndex("accountType")
                    val lastIdx = cursor.getColumnIndex("lastFourDigits")
                    val aliasIdx = cursor.getColumnIndex("senderAliases")
                    val createdIdx = cursor.getColumnIndex("createdAt")
                    val updatedIdx = cursor.getColumnIndex("updatedAt")
                    val systemIdx = cursor.getColumnIndex("systemAccountKey")
                    while (cursor.moveToNext()) {
                        if (!cursor.isNull(systemIdx)) continue
                        val accountId = cursor.getLong(idIdx)
                        val accountType = cursor.getString(typeIdx)
                        val createdAt = if (cursor.isNull(createdIdx)) now else cursor.getLong(createdIdx)
                        val updatedAt = if (cursor.isNull(updatedIdx)) now else cursor.getLong(updatedIdx)
                        val lastFour = cursor.getString(lastIdx)?.trim()
                        if (!lastFour.isNullOrEmpty() && lastFour.length == 4 && lastFour.all { it.isDigit() }) {
                            val idType = when (accountType) {
                                "CREDIT_CARD" -> "CREDIT_CARD_LAST4"
                                "DIGITAL_WALLET", "WALLET" -> "WALLET_LAST4"
                                else -> "ACCOUNT_LAST4"
                            }
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO `account_identifiers`
                                (`accountId`, `identifierType`, `normalizedValue`, `displayLabel`, `isActive`, `createdAt`, `updatedAt`)
                                VALUES (?, ?, ?, ?, 1, ?, ?)
                                """.trimIndent(),
                                arrayOf(accountId, idType, lastFour, lastFour, createdAt, updatedAt),
                            )
                        }
                        val aliases = cursor.getString(aliasIdx).orEmpty()
                        for (raw in aliases.split(",")) {
                            val alias = raw.trim()
                            if (alias.length < 2) continue
                            val key = alias.lowercase()
                                .map { ch ->
                                    when (ch) {
                                        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                                        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                                        else -> ch
                                    }
                                }
                                .filter { it.isLetterOrDigit() }
                                .joinToString("")
                                .take(64)
                            if (key.length < 2) continue
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO `account_identifiers`
                                (`accountId`, `identifierType`, `normalizedValue`, `displayLabel`, `isActive`, `createdAt`, `updatedAt`)
                                VALUES (?, 'SENDER_ALIAS', ?, ?, 1, ?, ?)
                                """.trimIndent(),
                                arrayOf(accountId, key, alias, createdAt, updatedAt),
                            )
                        }
                    }
                }

                // 2) Rebuild financial_accounts without legacy columns.
                db.execSQL(
                    """
                    CREATE TABLE `financial_accounts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `institutionName` TEXT,
                        `accountType` TEXT NOT NULL,
                        `accountNature` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `openingBalance` TEXT NOT NULL,
                        `openingBalanceDate` INTEGER NOT NULL,
                        `includeInNetWorth` INTEGER NOT NULL,
                        `includeInLiquidity` INTEGER NOT NULL,
                        `isOwnedByUser` INTEGER NOT NULL,
                        `systemAccountKey` TEXT,
                        `isActive` INTEGER NOT NULL,
                        `notes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `creditLimit` TEXT,
                        `openingBalanceKind` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `financial_accounts_new` (
                        `id`, `displayName`, `institutionName`, `accountType`, `accountNature`,
                        `currency`, `openingBalance`, `openingBalanceDate`, `includeInNetWorth`,
                        `includeInLiquidity`, `isOwnedByUser`, `systemAccountKey`, `isActive`,
                        `notes`, `createdAt`, `updatedAt`, `creditLimit`, `openingBalanceKind`
                    )
                    SELECT
                        `id`, `displayName`, `institutionName`, `accountType`, `accountNature`,
                        `currency`, `openingBalance`, `openingBalanceDate`, `includeInNetWorth`,
                        `includeInLiquidity`, `isOwnedByUser`, `systemAccountKey`, `isActive`,
                        `notes`, `createdAt`, `updatedAt`, `creditLimit`, `openingBalanceKind`
                    FROM `financial_accounts`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `financial_accounts`")
                db.execSQL("ALTER TABLE `financial_accounts_new` RENAME TO `financial_accounts`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_accounts_systemAccountKey` " +
                        "ON `financial_accounts` (`systemAccountKey`)",
                )
            }
        }

        /** Additive: AI deployment mode + on-device model path (no data loss). */
        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ai_settings` ADD COLUMN `deploymentMode` TEXT NOT NULL DEFAULT 'REMOTE'",
                )
                db.execSQL(
                    "ALTER TABLE `ai_settings` ADD COLUMN `onDeviceModelPath` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** Additive: local SMS body store for on-device link assist. */
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_sms_bodies` (
                        `transactionId` INTEGER NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Additive: teach-by-example SMS patterns (no raw bodies). */
        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sender_message_patterns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `senderKey` TEXT NOT NULL,
                        `accountId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `amountLabels` TEXT NOT NULL,
                        `typeCues` TEXT NOT NULL,
                        `lineLabels` TEXT NOT NULL,
                        `minScore` INTEGER NOT NULL,
                        `exampleCount` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sender_message_patterns_senderKey` ON `sender_message_patterns` (`senderKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sender_message_patterns_accountId` ON `sender_message_patterns` (`accountId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sender_message_patterns_senderKey_accountId_kind` ON `sender_message_patterns` (`senderKey`, `accountId`, `kind`)",
                )
            }
        }

        /**
         * Patterns belong to sender styles (structureKey), not accounts.
         * Recreates table: nullable accountId (no FK), unique(senderKey, structureKey, kind).
         */
        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sender_message_patterns_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `senderKey` TEXT NOT NULL,
                        `structureKey` TEXT NOT NULL,
                        `accountId` INTEGER,
                        `kind` TEXT NOT NULL,
                        `amountLabels` TEXT NOT NULL,
                        `typeCues` TEXT NOT NULL,
                        `lineLabels` TEXT NOT NULL,
                        `minScore` INTEGER NOT NULL,
                        `exampleCount` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `sender_message_patterns_new` (
                        `id`, `senderKey`, `structureKey`, `accountId`, `kind`,
                        `amountLabels`, `typeCues`, `lineLabels`,
                        `minScore`, `exampleCount`, `active`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        `id`,
                        `senderKey`,
                        CASE
                            WHEN length(trim(`lineLabels`)) > 0
                                THEN replace(`lineLabels`, char(10), '|')
                            ELSE 'legacy-' || `id`
                        END,
                        NULL,
                        `kind`,
                        `amountLabels`,
                        `typeCues`,
                        `lineLabels`,
                        `minScore`,
                        `exampleCount`,
                        `active`,
                        `createdAt`,
                        `updatedAt`
                    FROM `sender_message_patterns`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `sender_message_patterns`")
                db.execSQL("ALTER TABLE `sender_message_patterns_new` RENAME TO `sender_message_patterns`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sender_message_patterns_senderKey` ON `sender_message_patterns` (`senderKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sender_message_patterns_accountId` ON `sender_message_patterns` (`accountId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sender_message_patterns_senderKey_structureKey_kind` ON `sender_message_patterns` (`senderKey`, `structureKey`, `kind`)",
                )
            }
        }

        /**
         * SenderProfile + account↔sender many-to-many.
         * Idempotent backfill from active SENDER_ALIAS and institution mappings.
         */
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sender_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displaySender` TEXT NOT NULL,
                        `normalizedSenderKey` TEXT NOT NULL,
                        `institutionId` TEXT,
                        `displayInstitutionName` TEXT,
                        `active` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sender_profiles_normalizedSenderKey` ON `sender_profiles` (`normalizedSenderKey`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sender_profiles_active` ON `sender_profiles` (`active`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `account_sender_profiles` (
                        `accountId` INTEGER NOT NULL,
                        `senderProfileId` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`, `senderProfileId`),
                        FOREIGN KEY(`accountId`) REFERENCES `financial_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`senderProfileId`) REFERENCES `sender_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_account_sender_profiles_accountId` ON `account_sender_profiles` (`accountId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_account_sender_profiles_senderProfileId` ON `account_sender_profiles` (`senderProfileId`)",
                )
                // Backfill profiles from SENDER_ALIAS (distinct keys).
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sender_profiles (
                        displaySender, normalizedSenderKey, institutionId, displayInstitutionName,
                        active, createdAt, updatedAt
                    )
                    SELECT
                        MAX(ai.displayLabel),
                        ai.normalizedValue,
                        NULL,
                        (
                            SELECT m.institutionName FROM sender_institution_mapping m
                            WHERE m.senderKey = ai.normalizedValue AND m.isActive = 1
                            LIMIT 1
                        ),
                        1,
                        MIN(ai.createdAt),
                        MAX(ai.updatedAt)
                    FROM account_identifiers ai
                    WHERE ai.identifierType = 'SENDER_ALIAS'
                      AND ai.isActive = 1
                      AND length(trim(ai.normalizedValue)) > 0
                    GROUP BY ai.normalizedValue
                    """.trimIndent(),
                )
                // Also create profiles from institution mappings not already present.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sender_profiles (
                        displaySender, normalizedSenderKey, institutionId, displayInstitutionName,
                        active, createdAt, updatedAt
                    )
                    SELECT
                        m.senderKey,
                        m.senderKey,
                        NULL,
                        m.institutionName,
                        CASE WHEN m.isActive = 1 THEN 1 ELSE 0 END,
                        COALESCE(m.createdAt, 0),
                        COALESCE(m.lastConfirmedAt, m.createdAt, 0)
                    FROM sender_institution_mapping m
                    WHERE length(trim(m.senderKey)) > 0
                      AND NOT EXISTS (
                          SELECT 1 FROM sender_profiles sp
                          WHERE sp.normalizedSenderKey = m.senderKey
                      )
                    """.trimIndent(),
                )
                // Link accounts that had SENDER_ALIAS to the matching profile.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO account_sender_profiles (accountId, senderProfileId, createdAt)
                    SELECT ai.accountId, sp.id, COALESCE(ai.createdAt, 0)
                    FROM account_identifiers ai
                    INNER JOIN sender_profiles sp ON sp.normalizedSenderKey = ai.normalizedValue
                    WHERE ai.identifierType = 'SENDER_ALIAS' AND ai.isActive = 1
                    """.trimIndent(),
                )
            }
        }

        /** Message pattern definitions + field maps (labels only). */
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `message_pattern_definitions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `senderProfileId` INTEGER NOT NULL,
                        `userFriendlyName` TEXT NOT NULL,
                        `normalizedSignature` TEXT NOT NULL,
                        `transactionType` TEXT,
                        `direction` TEXT,
                        `channel` TEXT,
                        `status` TEXT NOT NULL,
                        `version` INTEGER NOT NULL,
                        `origin` TEXT NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `userConfirmed` INTEGER NOT NULL,
                        `exampleCount` INTEGER NOT NULL,
                        `activeFrom` INTEGER,
                        `deprecatedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`senderProfileId`) REFERENCES `sender_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_pattern_definitions_senderProfileId` ON `message_pattern_definitions` (`senderProfileId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_pattern_definitions_senderProfileId_normalizedSignature_version` ON `message_pattern_definitions` (`senderProfileId`, `normalizedSignature`, `version`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_pattern_definitions_status` ON `message_pattern_definitions` (`status`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pattern_field_definitions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `patternId` INTEGER NOT NULL,
                        `canonicalField` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `extractionStrategy` TEXT NOT NULL,
                        `required` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `valueType` TEXT NOT NULL,
                        FOREIGN KEY(`patternId`) REFERENCES `message_pattern_definitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pattern_field_definitions_patternId` ON `pattern_field_definitions` (`patternId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pattern_field_definitions_patternId_canonicalField_sourceLabel` ON `pattern_field_definitions` (`patternId`, `canonicalField`, `sourceLabel`)",
                )
                // Ensure profiles exist for taught pattern senders.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO sender_profiles (
                        displaySender, normalizedSenderKey, institutionId, displayInstitutionName,
                        active, createdAt, updatedAt
                    )
                    SELECT
                        p.senderKey,
                        p.senderKey,
                        NULL,
                        NULL,
                        1,
                        p.createdAt,
                        p.updatedAt
                    FROM sender_message_patterns p
                    WHERE p.active = 1
                      AND length(trim(p.senderKey)) > 0
                      AND NOT EXISTS (
                          SELECT 1 FROM sender_profiles sp
                          WHERE sp.normalizedSenderKey = p.senderKey
                      )
                    """.trimIndent(),
                )
                // Migrate INCLUDE patterns → APPROVED definitions (signature = structureKey).
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO message_pattern_definitions (
                        senderProfileId, userFriendlyName, normalizedSignature,
                        transactionType, direction, channel, status, version, origin,
                        confidence, userConfirmed, exampleCount, activeFrom, deprecatedAt,
                        createdAt, updatedAt
                    )
                    SELECT
                        sp.id,
                        CASE
                            WHEN length(trim(p.typeCues)) > 0
                                THEN replace(substr(p.typeCues, 1, instr(p.typeCues || char(10), char(10)) - 1), char(10), '')
                            ELSE 'نمط مستورد'
                        END,
                        p.structureKey,
                        NULL, NULL, NULL,
                        'APPROVED',
                        1,
                        'MIGRATED',
                        50,
                        1,
                        p.exampleCount,
                        p.createdAt,
                        NULL,
                        p.createdAt,
                        p.updatedAt
                    FROM sender_message_patterns p
                    INNER JOIN sender_profiles sp ON sp.normalizedSenderKey = p.senderKey
                    WHERE p.active = 1 AND p.kind = 'INCLUDE_TRANSACTION'
                    """.trimIndent(),
                )
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
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
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
