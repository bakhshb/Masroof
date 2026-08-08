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
 * Schema migrations are incremental and preserve all financial records.
 * No `fallbackToDestructiveMigration` is configured.
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
        SenderProfileEntity::class,
        AccountSenderProfileCrossRef::class,
        MessagePatternDefinitionEntity::class,
        PatternFieldDefinitionEntity::class,
        MessagePatternFamilyEntity::class,
        PatternVariantAnchorEntity::class,
    ],
    version = 31,
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
    abstract fun senderProfileDao(): SenderProfileDao
    abstract fun accountSenderProfileDao(): AccountSenderProfileDao
    abstract fun messagePatternDefinitionDao(): MessagePatternDefinitionDao
    abstract fun patternFieldDefinitionDao(): PatternFieldDefinitionDao
    abstract fun messagePatternFamilyDao(): MessagePatternFamilyDao
    abstract fun patternVariantAnchorDao(): PatternVariantAnchorDao

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

        /**
         * Finish legacy flat-pattern retirement:
         * migrate IGNORE rows + amount labels into definition tables, then drop
         * `sender_message_patterns`. Historical migrations 17→21 keep creating
         * that table for upgrades from older installs.
         */
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // IGNORE_AUTH → IGNORED definitions (signature = structureKey).
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
                        'تجاهل / تحقق',
                        p.structureKey,
                        NULL, NULL, NULL,
                        'IGNORED',
                        1,
                        'MIGRATED',
                        50,
                        1,
                        p.exampleCount,
                        NULL,
                        NULL,
                        p.createdAt,
                        p.updatedAt
                    FROM sender_message_patterns p
                    INNER JOIN sender_profiles sp ON sp.normalizedSenderKey = p.senderKey
                    WHERE p.active = 1 AND p.kind = 'IGNORE_AUTH'
                    """.trimIndent(),
                )
                // Backfill TRANSACTION_AMOUNT labels from legacy amountLabels (newline list).
                val cursor = db.query(
                    """
                    SELECT mpd.id AS patternId, p.amountLabels AS amountLabels
                    FROM sender_message_patterns p
                    INNER JOIN sender_profiles sp ON sp.normalizedSenderKey = p.senderKey
                    INNER JOIN message_pattern_definitions mpd
                        ON mpd.senderProfileId = sp.id
                        AND mpd.normalizedSignature = p.structureKey
                        AND mpd.version = 1
                    WHERE p.active = 1
                      AND p.kind = 'INCLUDE_TRANSACTION'
                      AND length(trim(p.amountLabels)) > 0
                    """.trimIndent(),
                )
                cursor.use { c ->
                    val patternIdIdx = c.getColumnIndexOrThrow("patternId")
                    val labelsIdx = c.getColumnIndexOrThrow("amountLabels")
                    while (c.moveToNext()) {
                        val patternId = c.getLong(patternIdIdx)
                        val raw = c.getString(labelsIdx).orEmpty()
                        for (label in raw.split('\n')) {
                            val trimmed = label.trim()
                            if (trimmed.isEmpty()) continue
                            val escaped = trimmed.replace("'", "''")
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO pattern_field_definitions (
                                    patternId, canonicalField, sourceLabel,
                                    extractionStrategy, required, role, valueType
                                ) VALUES (
                                    $patternId,
                                    'TRANSACTION_AMOUNT',
                                    '$escaped',
                                    'LABELED_LINE',
                                    0,
                                    'PRIMARY',
                                    'MONEY'
                                )
                                """.trimIndent(),
                            )
                        }
                    }
                }
                db.execSQL("DROP TABLE IF EXISTS `sender_message_patterns`")
            }
        }

        /**
         * Remove deprecated SENDER_ALIAS identifier rows. Sender identity is
         * exclusively SenderProfile + account_sender_profiles (backfilled in v20).
         */
        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure profiles exist for any remaining alias keys before delete.
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
                        NULL,
                        1,
                        MIN(ai.createdAt),
                        MAX(ai.updatedAt)
                    FROM account_identifiers ai
                    WHERE ai.identifierType = 'SENDER_ALIAS'
                      AND ai.isActive = 1
                      AND length(trim(ai.normalizedValue)) > 0
                      AND NOT EXISTS (
                          SELECT 1 FROM sender_profiles sp
                          WHERE sp.normalizedSenderKey = ai.normalizedValue
                      )
                    GROUP BY ai.normalizedValue
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO account_sender_profiles (accountId, senderProfileId, createdAt)
                    SELECT ai.accountId, sp.id, COALESCE(ai.createdAt, 0)
                    FROM account_identifiers ai
                    INNER JOIN sender_profiles sp ON sp.normalizedSenderKey = ai.normalizedValue
                    WHERE ai.identifierType = 'SENDER_ALIAS' AND ai.isActive = 1
                    """.trimIndent(),
                )
                db.execSQL("DELETE FROM account_identifiers WHERE identifierType = 'SENDER_ALIAS'")
            }
        }

        val MIGRATION_23_24: Migration = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: human-readable structural template for patterns.
                db.execSQL(
                    "ALTER TABLE message_pattern_definitions ADD COLUMN templateText TEXT",
                )
            }
        }

        /**
         * v24 → v25: canonical pattern identity + duplicate merge.
         *
         * Adds `canonicalKey` (canonicalized templateText, or signature fallback),
         * merges rows that share (senderProfileId, canonicalKey) — preserving
         * user-confirmed statuses, edits, field definitions, and summed counts —
         * then enforces uniqueness with an index. Non-destructive: only exact
         * structural duplicates are merged, never distinct patterns.
         */
        val MIGRATION_24_25: Migration = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE message_pattern_definitions ADD COLUMN canonicalKey TEXT NOT NULL DEFAULT ''",
                )

                val rows = mutableListOf<PatternDuplicateMerger.MergeRow>()
                db.query(
                    """
                    SELECT id, senderProfileId, normalizedSignature, templateText,
                           status, userConfirmed, exampleCount, createdAt
                    FROM message_pattern_definitions
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        val template = if (c.isNull(3)) null else c.getString(3)
                        rows += PatternDuplicateMerger.MergeRow(
                            id = c.getLong(0),
                            senderProfileId = c.getLong(1),
                            canonicalKey = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                                template,
                                c.getString(2).orEmpty(),
                            ),
                            status = c.getString(4).orEmpty(),
                            userConfirmed = c.getInt(5) != 0,
                            exampleCount = c.getInt(6),
                            createdAt = c.getLong(7),
                        )
                    }
                }

                val now = System.currentTimeMillis()
                rows.groupBy { it.senderProfileId to it.canonicalKey }.values.forEach { group ->
                    val survivor = PatternDuplicateMerger.selectSurvivor(group)
                    val losers = group.filter { it.id != survivor.id }
                    val totalCount = PatternDuplicateMerger.mergedExampleCount(group)
                    val escapedKey = survivor.canonicalKey.replace("'", "''")

                    if (losers.isNotEmpty()) {
                        // Preserve field definitions: if the survivor has none,
                        // adopt them from the first duplicate that has some.
                        val survivorFieldCount = db.query(
                            "SELECT COUNT(*) FROM pattern_field_definitions WHERE patternId = ${survivor.id}",
                        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                        if (survivorFieldCount == 0L) {
                            for (loser in losers) {
                                val loserFieldCount = db.query(
                                    "SELECT COUNT(*) FROM pattern_field_definitions WHERE patternId = ${loser.id}",
                                ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                                if (loserFieldCount > 0L) {
                                    db.execSQL(
                                        "UPDATE pattern_field_definitions SET patternId = ${survivor.id} " +
                                            "WHERE patternId = ${loser.id}",
                                    )
                                    break
                                }
                            }
                        }
                        val loserIds = losers.joinToString(",") { it.id.toString() }
                        // Explicit deletes: FK cascade may be deferred during migration.
                        db.execSQL("DELETE FROM pattern_field_definitions WHERE patternId IN ($loserIds)")
                        db.execSQL("DELETE FROM message_pattern_definitions WHERE id IN ($loserIds)")
                    }

                    db.execSQL(
                        """
                        UPDATE message_pattern_definitions
                        SET canonicalKey = '$escapedKey',
                            exampleCount = $totalCount,
                            updatedAt = $now
                        WHERE id = ${survivor.id}
                        """.trimIndent(),
                    )
                }

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_message_pattern_definitions_senderProfileId_canonicalKey` " +
                        "ON `message_pattern_definitions` (`senderProfileId`, `canonicalKey`)",
                )
            }
        }

        /**
         * v25 → v26: recompute canonicalKey as a **semantic family** signature
         * (type + core field roles). Wallet/channel and optional balance/due
         * lines no longer split identity.
         *
         * Non-destructive plan:
         * 1. Recompute key per row from templateText + transactionType
         *    (signature fallback for legacy null templates).
         * 2. Merge duplicates per (senderProfileId, newKey) with the same
         *    survivor rules as v24→v25 (userConfirmed → status → earliest).
         * 3. Sum exampleCount; re-point field definitions onto the survivor
         *    when it has none; delete only duplicate losers.
         * 4. Strip wallet suffixes from userFriendlyName ("· Google Pay").
         * 5. Unique index already exists — no schema drop.
         *
         * Idempotent: re-running the key formula yields the same family key.
         */
        val MIGRATION_25_26: Migration = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                data class FullRow(
                    val merge: PatternDuplicateMerger.MergeRow,
                    val templateText: String?,
                    val friendlyName: String,
                    val transactionType: String?,
                )
                val rows = mutableListOf<FullRow>()
                db.query(
                    """
                    SELECT id, senderProfileId, normalizedSignature, templateText,
                           status, userConfirmed, exampleCount, createdAt,
                           userFriendlyName, transactionType
                    FROM message_pattern_definitions
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        val template = if (c.isNull(3)) null else c.getString(3)
                        val txType = if (c.isNull(9)) null else c.getString(9)
                        val key = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                            template,
                            c.getString(2).orEmpty(),
                            txType,
                        )
                        rows += FullRow(
                            merge = PatternDuplicateMerger.MergeRow(
                                id = c.getLong(0),
                                senderProfileId = c.getLong(1),
                                canonicalKey = key,
                                status = c.getString(4).orEmpty(),
                                userConfirmed = c.getInt(5) != 0,
                                exampleCount = c.getInt(6),
                                createdAt = c.getLong(7),
                            ),
                            templateText = template,
                            friendlyName = c.getString(8).orEmpty(),
                            transactionType = txType,
                        )
                    }
                }

                val now = System.currentTimeMillis()
                rows.groupBy { it.merge.senderProfileId to it.merge.canonicalKey }.values.forEach { group ->
                    val survivorFull = group.sortedWith(
                        compareByDescending<FullRow> { it.merge.userConfirmed }
                            .thenBy { PatternDuplicateMerger.statusPriority(it.merge.status) }
                            .thenBy { it.merge.createdAt }
                            .thenBy { it.merge.id },
                    ).first()
                    val survivor = survivorFull.merge
                    val losers = group.filter { it.merge.id != survivor.id }
                    val totalCount = group.sumOf { it.merge.exampleCount }
                    val escapedKey = survivor.canonicalKey.replace("'", "''")
                    val cleanName = stripWalletFromDisplayName(survivorFull.friendlyName)
                        .replace("'", "''")

                    if (losers.isNotEmpty()) {
                        val survivorFieldCount = db.query(
                            "SELECT COUNT(*) FROM pattern_field_definitions WHERE patternId = ${survivor.id}",
                        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                        if (survivorFieldCount == 0L) {
                            for (loser in losers) {
                                val loserFieldCount = db.query(
                                    "SELECT COUNT(*) FROM pattern_field_definitions WHERE patternId = ${loser.merge.id}",
                                ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                                if (loserFieldCount > 0L) {
                                    db.execSQL(
                                        "UPDATE pattern_field_definitions SET patternId = ${survivor.id} " +
                                            "WHERE patternId = ${loser.merge.id}",
                                    )
                                    break
                                }
                            }
                        }
                        val loserIds = losers.joinToString(",") { it.merge.id.toString() }
                        db.execSQL("DELETE FROM pattern_field_definitions WHERE patternId IN ($loserIds)")
                        db.execSQL("DELETE FROM message_pattern_definitions WHERE id IN ($loserIds)")
                    }

                    db.execSQL(
                        """
                        UPDATE message_pattern_definitions
                        SET canonicalKey = '$escapedKey',
                            userFriendlyName = '$cleanName',
                            exampleCount = $totalCount,
                            updatedAt = $now
                        WHERE id = ${survivor.id}
                        """.trimIndent(),
                    )
                }
            }

            private fun stripWalletFromDisplayName(name: String): String {
                val cut = name.indexOf(" · ")
                return if (cut > 0) name.substring(0, cut).trim() else name.trim()
            }
        }

        /**
         * v26 → v27: canonical taxonomy plus immutable template revisions.
         *
         * Financial history is intentionally untouched: transaction fingerprints,
         * links, balances, and journal rows are never rewritten. Only stored type
         * names are canonicalized.
         */
        val MIGRATION_26_27: Migration = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun canonicalType(raw: String?): String? = when (raw) {
                    "BANK_FEE" -> "FEE"
                    "CREDIT_LIMIT_CHANGE", "DECLINED" -> "NON_FINANCIAL"
                    "INVESTMENT_TRANSFER" -> "INTERNAL_TRANSFER"
                    "LOAN_INSTALLMENT" -> "BILL_PAYMENT"
                    "DEPOSIT", "UNKNOWN" -> "OTHER_FINANCIAL"
                    else -> raw
                }

                // Canonicalize transaction rows without touching treatment, status,
                // fingerprints, links, or posted journal references.
                db.execSQL(
                    """
                    UPDATE transactions
                    SET transactionType = CASE transactionType
                        WHEN 'BANK_FEE' THEN 'FEE'
                        WHEN 'CREDIT_LIMIT_CHANGE' THEN 'NON_FINANCIAL'
                        WHEN 'DECLINED' THEN 'NON_FINANCIAL'
                        WHEN 'INVESTMENT_TRANSFER' THEN 'INTERNAL_TRANSFER'
                        WHEN 'LOAN_INSTALLMENT' THEN 'BILL_PAYMENT'
                        WHEN 'DEPOSIT' THEN 'OTHER_FINANCIAL'
                        WHEN 'UNKNOWN' THEN 'OTHER_FINANCIAL'
                        ELSE transactionType END
                    """.trimIndent(),
                )

                // Permit multiple immutable revisions for one canonical family.
                db.execSQL(
                    "DROP INDEX IF EXISTS `index_message_pattern_definitions_senderProfileId_canonicalKey`",
                )
                db.execSQL(
                    "ALTER TABLE `message_pattern_definitions` " +
                        "ADD COLUMN `lineageId` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `message_pattern_definitions` " +
                        "ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `pattern_field_definitions` " +
                        "ADD COLUMN `placeholderToken` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    """
                    UPDATE pattern_field_definitions
                    SET placeholderToken = CASE canonicalField
                        WHEN 'TRANSACTION_AMOUNT' THEN 'AMOUNT'
                        WHEN 'TRANSACTION_DATE' THEN 'DATE'
                        WHEN 'TRANSACTION_TIME' THEN 'TIME'
                        WHEN 'TRANSACTION_REFERENCE' THEN 'REFERENCE'
                        WHEN 'CARD_AMOUNT_DUE' THEN 'TOTAL_DUE'
                        ELSE canonicalField END
                    """.trimIndent(),
                )

                data class PatternRow(
                    val id: Long,
                    val senderId: Long,
                    val signature: String,
                    val template: String?,
                    val type: String?,
                    val createdAt: Long,
                )
                val patternRows = mutableListOf<PatternRow>()
                db.query(
                    """
                    SELECT id, senderProfileId, normalizedSignature, templateText,
                           transactionType, createdAt
                    FROM message_pattern_definitions
                    ORDER BY createdAt, id
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        patternRows += PatternRow(
                            id = c.getLong(0),
                            senderId = c.getLong(1),
                            signature = c.getString(2).orEmpty(),
                            template = if (c.isNull(3)) null else c.getString(3),
                            type = if (c.isNull(4)) null else canonicalType(c.getString(4)),
                            createdAt = c.getLong(5),
                        )
                    }
                }

                data class CanonicalPattern(val row: PatternRow, val key: String)
                val canonicalPatterns = patternRows.map { row ->
                    CanonicalPattern(
                        row = row,
                        key = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                            row.template,
                            row.signature,
                            row.type,
                        ),
                    )
                }
                canonicalPatterns
                    .groupBy { it.row.senderId to it.key }
                    .values
                    .forEach { family ->
                        val ordered = family.sortedWith(
                            compareBy<CanonicalPattern> { it.row.createdAt }.thenBy { it.row.id },
                        )
                        val lineageId = ordered.first().row.id
                        ordered.forEachIndexed { index, item ->
                            val escapedKey = item.key.replace("'", "''")
                            val typeSql = item.row.type
                                ?.replace("'", "''")
                                ?.let { "'$it'" }
                                ?: "NULL"
                            db.execSQL(
                                """
                                UPDATE message_pattern_definitions
                                SET canonicalKey = '$escapedKey',
                                    transactionType = $typeSql,
                                    direction = CASE direction
                                        WHEN 'IN' THEN 'INFLOW'
                                        WHEN 'OUT' THEN 'OUTFLOW'
                                        WHEN 'INTERNAL' THEN 'TRANSFER'
                                        ELSE direction END,
                                    lineageId = $lineageId,
                                    version = ${index + 1},
                                    isActive = CASE
                                        WHEN templateText IS NOT NULL
                                             AND length(trim(templateText)) > 0
                                             AND status = 'APPROVED'
                                             AND deprecatedAt IS NULL
                                        THEN 1 ELSE 0 END
                                WHERE id = ${item.row.id}
                                """.trimIndent(),
                            )
                        }
                    }

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_pattern_definitions_lineageId` " +
                        "ON `message_pattern_definitions` (`lineageId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_message_pattern_definitions_senderProfileId_canonicalKey_version` " +
                        "ON `message_pattern_definitions` (`senderProfileId`, `canonicalKey`, `version`)",
                )

                // Canonicalize learned link rules. Drop uniqueness temporarily so
                // collisions can be resolved without selecting a conflicting account.
                db.execSQL("DROP INDEX IF EXISTS `index_account_link_rules_signature`")
                data class LinkRule(
                    val id: Long,
                    val signature: String,
                    val type: String,
                    val accountId: Long,
                    val confirmations: Int,
                    val lastConfirmedAt: Long,
                    val currentShape: Boolean,
                )
                val rules = mutableListOf<LinkRule>()
                db.query(
                    """
                    SELECT id, signature, transactionType, accountId,
                           confirmationCount, lastConfirmedAt
                    FROM account_link_rules
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        val type = canonicalType(c.getString(2)) ?: "OTHER_FINANCIAL"
                        val parts = c.getString(1).orEmpty().split('|').toMutableList()
                        if (parts.size >= 2) parts[1] = type
                        val currentShape = parts.size >= 5
                        val canonicalSignature = if (currentShape) {
                            parts.joinToString("|")
                        } else {
                            "${parts.joinToString("|")}|retired:${c.getLong(0)}"
                        }
                        rules += LinkRule(
                            id = c.getLong(0),
                            signature = canonicalSignature,
                            type = type,
                            accountId = c.getLong(3),
                            confirmations = c.getInt(4),
                            lastConfirmedAt = c.getLong(5),
                            currentShape = currentShape,
                        )
                    }
                }
                rules.groupBy { it.signature }.forEach { (signature, group) ->
                    val escaped = signature.replace("'", "''")
                    if (group.map { it.accountId }.distinct().size == 1) {
                        val survivor = group.maxWithOrNull(
                            compareBy<LinkRule> { it.lastConfirmedAt }.thenBy { it.id },
                        )!!
                        val total = group.sumOf { it.confirmations }
                        val loserIds = group.filter { it.id != survivor.id }
                            .joinToString(",") { it.id.toString() }
                        db.execSQL(
                            """
                            UPDATE account_link_rules
                            SET signature = '$escaped',
                                transactionType = '${survivor.type}',
                                confirmationCount = $total,
                                active = ${if (survivor.currentShape) 1 else 0}
                            WHERE id = ${survivor.id}
                            """.trimIndent(),
                        )
                        if (loserIds.isNotEmpty()) {
                            db.execSQL("DELETE FROM account_link_rules WHERE id IN ($loserIds)")
                        }
                    } else {
                        // Conflicting learned accounts require explicit reconfirmation.
                        group.forEach { rule ->
                            val conflictSignature =
                                "${signature}|conflict:${rule.id}".replace("'", "''")
                            db.execSQL(
                                """
                                UPDATE account_link_rules
                                SET signature = '$conflictSignature',
                                    transactionType = '${rule.type}',
                                    active = 0
                                WHERE id = ${rule.id}
                                """.trimIndent(),
                            )
                        }
                    }
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_account_link_rules_signature` " +
                        "ON `account_link_rules` (`signature`)",
                )
            }
        }

        /**
         * v27 → v28 introduces the sender-scoped Family → Variant model.
         * Existing definitions are preserved verbatim as variants; no pattern,
         * field, account, transaction, journal, or posting is deleted.
         */
        val MIGRATION_27_28: Migration = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_pattern_families` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`senderProfileId` INTEGER NOT NULL, `stableKey` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`senderProfileId`) REFERENCES `sender_profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_pattern_families_senderProfileId` ON `message_pattern_families` (`senderProfileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_message_pattern_families_senderProfileId_stableKey` ON `message_pattern_families` (`senderProfileId`, `stableKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_pattern_families_status` ON `message_pattern_families` (`status`)")

                data class OldPattern(
                    val id: Long,
                    val senderId: Long,
                    val name: String,
                    val signature: String,
                    val template: String?,
                    val status: String,
                    val transactionType: String?,
                    val direction: String?,
                    val channel: String?,
                    val createdAt: Long,
                    val updatedAt: Long,
                )
                val old = mutableListOf<OldPattern>()
                db.query(
                    "SELECT id, senderProfileId, userFriendlyName, normalizedSignature, templateText, status, " +
                        "transactionType, direction, channel, createdAt, updatedAt " +
                        "FROM message_pattern_definitions ORDER BY id",
                ).use { c ->
                    while (c.moveToNext()) {
                        old += OldPattern(
                            id = c.getLong(0),
                            senderId = c.getLong(1),
                            name = c.getString(2).orEmpty(),
                            signature = c.getString(3).orEmpty(),
                            template = if (c.isNull(4)) null else c.getString(4),
                            status = c.getString(5),
                            transactionType = if (c.isNull(6)) null else c.getString(6),
                            direction = if (c.isNull(7)) null else c.getString(7),
                            channel = if (c.isNull(8)) null else c.getString(8),
                            createdAt = c.getLong(9),
                            updatedAt = c.getLong(10),
                        )
                    }
                }
                val familyByPattern = mutableMapOf<Long, Long>()
                old.forEach { row ->
                    val key = semanticFamilyKey(
                        transactionType = row.transactionType,
                        direction = row.direction,
                        channel = row.channel,
                        templateText = row.template,
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO message_pattern_families (senderProfileId, stableKey, displayName, status, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(row.senderId, key, row.name.ifBlank { "نمط رسالة" }, row.status, row.createdAt, row.updatedAt),
                    )
                    val familyId = db.query(
                        "SELECT id FROM message_pattern_families WHERE senderProfileId = ? AND stableKey = ? LIMIT 1",
                        arrayOf<Any>(row.senderId, key),
                    ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                    if (familyId > 0L) {
                        familyByPattern[row.id] = familyId
                        if (row.status == "APPROVED") {
                            db.execSQL("UPDATE message_pattern_families SET status = 'APPROVED', updatedAt = ? WHERE id = ?", arrayOf(row.updatedAt, familyId))
                        }
                    }
                }

                // Rebuild variant table only to add the Family FK. All data is copied.
                db.execSQL(
                    "CREATE TABLE `message_pattern_definitions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderProfileId` INTEGER NOT NULL, `familyId` INTEGER, " +
                        "`userFriendlyName` TEXT NOT NULL, `normalizedSignature` TEXT NOT NULL, `canonicalKey` TEXT NOT NULL, " +
                        "`lineageId` INTEGER NOT NULL, `templateText` TEXT, `transactionType` TEXT, `direction` TEXT, `channel` TEXT, " +
                        "`status` TEXT NOT NULL, `version` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `origin` TEXT NOT NULL, " +
                        "`confidence` INTEGER NOT NULL, `userConfirmed` INTEGER NOT NULL, `exampleCount` INTEGER NOT NULL, " +
                        "`activeFrom` INTEGER, `deprecatedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`senderProfileId`) REFERENCES `sender_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`familyId`) REFERENCES `message_pattern_families`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
                )
                old.forEach { row ->
                    val familyId = familyByPattern[row.id]
                    db.execSQL(
                        "INSERT INTO message_pattern_definitions_new (id,senderProfileId,familyId,userFriendlyName,normalizedSignature,canonicalKey,lineageId,templateText,transactionType,direction,channel,status,version,isActive,origin,confidence,userConfirmed,exampleCount,activeFrom,deprecatedAt,createdAt,updatedAt) SELECT id,senderProfileId,?,userFriendlyName,normalizedSignature,canonicalKey,lineageId,templateText,transactionType,direction,channel,status,version,isActive,origin,confidence,userConfirmed,exampleCount,activeFrom,deprecatedAt,createdAt,updatedAt FROM message_pattern_definitions WHERE id = ?",
                        arrayOf(familyId, row.id),
                    )
                }
                db.execSQL(
                    "CREATE TABLE `pattern_field_definitions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patternId` INTEGER NOT NULL, `canonicalField` TEXT NOT NULL, " +
                        "`placeholderToken` TEXT NOT NULL, `sourceLabel` TEXT NOT NULL, `extractionStrategy` TEXT NOT NULL, " +
                        "`required` INTEGER NOT NULL, `role` TEXT NOT NULL, `valueType` TEXT NOT NULL, " +
                        "FOREIGN KEY(`patternId`) REFERENCES `message_pattern_definitions_new`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("INSERT INTO pattern_field_definitions_new SELECT id,patternId,canonicalField,placeholderToken,sourceLabel,extractionStrategy,required,role,valueType FROM pattern_field_definitions")
                db.execSQL("DROP TABLE pattern_field_definitions")
                db.execSQL("DROP TABLE message_pattern_definitions")
                db.execSQL("ALTER TABLE message_pattern_definitions_new RENAME TO message_pattern_definitions")
                db.execSQL("ALTER TABLE pattern_field_definitions_new RENAME TO pattern_field_definitions")
                db.execSQL("CREATE INDEX `index_message_pattern_definitions_senderProfileId` ON message_pattern_definitions (`senderProfileId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_message_pattern_definitions_senderProfileId_normalizedSignature_version` ON message_pattern_definitions (`senderProfileId`,`normalizedSignature`,`version`)")
                db.execSQL("CREATE UNIQUE INDEX `index_message_pattern_definitions_senderProfileId_canonicalKey_version` ON message_pattern_definitions (`senderProfileId`,`canonicalKey`,`version`)")
                db.execSQL("CREATE INDEX `index_message_pattern_definitions_familyId` ON message_pattern_definitions (`familyId`)")
                db.execSQL("CREATE INDEX `index_message_pattern_definitions_lineageId` ON message_pattern_definitions (`lineageId`)")
                db.execSQL("CREATE INDEX `index_message_pattern_definitions_status` ON message_pattern_definitions (`status`)")
                db.execSQL("CREATE INDEX `index_pattern_field_definitions_patternId` ON pattern_field_definitions (`patternId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_pattern_field_definitions_patternId_canonicalField_sourceLabel` ON pattern_field_definitions (`patternId`,`canonicalField`,`sourceLabel`)")

                db.execSQL(
                    "CREATE TABLE `pattern_variant_anchors` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`variantId` INTEGER NOT NULL, `normalizedAnchor` TEXT NOT NULL, `required` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`variantId`) REFERENCES `message_pattern_definitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX `index_pattern_variant_anchors_variantId` ON pattern_variant_anchors (`variantId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_pattern_variant_anchors_variantId_normalizedAnchor` ON pattern_variant_anchors (`variantId`,`normalizedAnchor`)")
                old.forEach { row ->
                    com.baraa.masroof.sms.PatternStructure.anchorsFromTemplate(row.template).forEach { (anchor, required) ->
                        db.execSQL("INSERT OR IGNORE INTO pattern_variant_anchors (variantId,normalizedAnchor,required) VALUES (?,?,?)", arrayOf(row.id, anchor, if (required) 1 else 0))
                    }
                }
            }
        }

        /**
         * v28 → v29 introduces normalization versioning and re-keys
         * [MessagePatternFamilyEntity] by semantic identity
         * (transactionType + direction + paymentInstrument + channel).
         *
         * Non-destructive plan:
         * 1. Add `normalizationVersion` column (default 0 = pre-versioned).
         *    Existing rows keep version 0 — they are surfaced as STALE
         *    by [com.baraa.masroof.sms.NORMALIZATION_VERSION] lookup and
         *    are eligible for the rebuild flow.
         * 2. Recompute each family's stableKey from its first non-null
         *    variant's (transactionType + direction + paymentInstrument).
         *    When two existing families collide on the new key, the
         *    later-created one is merged into the earlier (their variants
         *    are re-pointed to the survivor, account rows untouched).
         * 3. Variants whose family was merged keep their id, templateText,
         *    canonicalKey, fields, and anchors — only their `familyId`
         *    FK is updated.
         *
         * The transaction table, journal table, ledger postings, financial
         * accounts, and identifiers are NEVER touched.
         */
        val MIGRATION_28_29: Migration = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE message_pattern_definitions " +
                        "ADD COLUMN normalizationVersion INTEGER NOT NULL DEFAULT 0",
                )

                data class FamilyRow(
                    val id: Long,
                    val senderId: Long,
                    val stableKey: String,
                    val displayName: String,
                    val status: String,
                    val createdAt: Long,
                    val updatedAt: Long,
                )

                val families = mutableListOf<FamilyRow>()
                db.query(
                    "SELECT id, senderProfileId, stableKey, displayName, status, createdAt, updatedAt " +
                        "FROM message_pattern_families ORDER BY id"
                ).use { c ->
                    while (c.moveToNext()) {
                        families += FamilyRow(
                            c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                            c.getString(4), c.getLong(5), c.getLong(6),
                        )
                    }
                }

                data class VariantRow(
                    val id: Long,
                    val familyId: Long?,
                    val transactionType: String?,
                    val direction: String?,
                    val channel: String?,
                    val templateText: String?,
                )
                val variants = mutableMapOf<Long, VariantRow>()
                db.query(
                    "SELECT id, familyId, transactionType, direction, channel, templateText " +
                        "FROM message_pattern_definitions WHERE familyId IS NOT NULL ORDER BY id"
                ).use { c ->
                    while (c.moveToNext()) {
                        val type = if (c.isNull(2)) null else c.getString(2)
                        val direction = if (c.isNull(3)) null else c.getString(3)
                        val channel = if (c.isNull(4)) null else c.getString(4)
                        val template = if (c.isNull(5)) null else c.getString(5)
                        variants[c.getLong(0)] = VariantRow(
                            c.getLong(0),
                            if (c.isNull(1)) null else c.getLong(1),
                            type,
                            direction,
                            channel,
                            template,
                        )
                    }
                }

                val survivorByKey = mutableMapOf<Pair<Long, String>, Long>()
                val familyIdRemap = mutableMapOf<Long, Long>()
                val now = System.currentTimeMillis()

                families.forEach { family ->
                    val member = variants.values.firstOrNull { it.familyId == family.id }
                    val newKey = semanticFamilyKey(
                        transactionType = member?.transactionType,
                        direction = member?.direction,
                        channel = member?.channel,
                        templateText = member?.templateText,
                    )
                    val composite = family.senderId to newKey
                    val survivor = survivorByKey[composite]
                    if (survivor == null) {
                        survivorByKey[composite] = family.id
                        familyIdRemap[family.id] = family.id
                        if (family.stableKey != newKey) {
                            val escaped = newKey.replace("'", "''")
                            db.execSQL(
                                "UPDATE message_pattern_families SET stableKey = '$escaped', updatedAt = $now " +
                                    "WHERE id = ${family.id}",
                            )
                        }
                    } else {
                        familyIdRemap[family.id] = survivor
                    }
                }

                // Re-point variant FKs and remove duplicate families.
                val droppedFamilies = familyIdRemap.filter { it.key != it.value }
                for ((oldId, newId) in droppedFamilies) {
                    db.execSQL(
                        "UPDATE message_pattern_definitions SET familyId = $newId " +
                            "WHERE familyId = $oldId",
                    )
                    db.execSQL("DELETE FROM message_pattern_families WHERE id = $oldId")
                }

                // Defensive: drop the old per-(sender, key) unique index that
                // was tied to the legacy single-label key. The new key uses
                // the same column pair, so the index can stay; we only need
                // to clean any stale duplicates the migration couldn't merge
                // because they were referenced from a variant's history.
                db.execSQL(
                    "DELETE FROM message_pattern_families WHERE id NOT IN " +
                        "(SELECT familyId FROM message_pattern_definitions WHERE familyId IS NOT NULL " +
                        "UNION SELECT id FROM message_pattern_families WHERE id NOT IN " +
                        "(SELECT DISTINCT familyId FROM message_pattern_definitions WHERE familyId IS NOT NULL))",
                )
            }
        }

        /**
         * v29 -> v30 re-keys user-visible families with semantic identity.
         *
         * Definitions, fields, anchors, revisions, counts, and approval state
         * are preserved. Only family rows and definition.familyId links change.
         * Financial tables are not read or written.
         */
        val MIGRATION_29_30: Migration = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                data class Variant(
                    val id: Long,
                    val senderId: Long,
                    val oldFamilyId: Long?,
                    val name: String,
                    val status: String,
                    val template: String?,
                    val type: String?,
                    val createdAt: Long,
                    val updatedAt: Long,
                )

                val variants = mutableListOf<Variant>()
                db.query(
                    """
                    SELECT id, senderProfileId, familyId, userFriendlyName, status,
                           templateText, transactionType, createdAt, updatedAt
                    FROM message_pattern_definitions
                    ORDER BY id
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        variants += Variant(
                            id = c.getLong(0),
                            senderId = c.getLong(1),
                            oldFamilyId = if (c.isNull(2)) null else c.getLong(2),
                            name = c.getString(3).orEmpty(),
                            status = c.getString(4),
                            template = if (c.isNull(5)) null else c.getString(5),
                            type = if (c.isNull(6)) null else c.getString(6),
                            createdAt = c.getLong(7),
                            updatedAt = c.getLong(8),
                        )
                    }
                }

                // Free the unique (sender, stableKey) namespace while all old
                // family ids remain valid until definitions are re-pointed.
                db.execSQL(
                    "UPDATE message_pattern_families " +
                        "SET stableKey = 'legacy-v29-family:' || id",
                )

                data class SemanticGroup(val senderId: Long, val key: String)
                val groupForVariant = linkedMapOf<Long, SemanticGroup>()
                variants.forEach { variant ->
                    val semantic = com.baraa.masroof.sms.SemanticPatternSchemaNormalizer
                        .fromTemplate(variant.template, variant.type)
                    val key = when (semantic) {
                        is com.baraa.masroof.sms.SemanticSchemaResult.Safe -> semantic.key
                        is com.baraa.masroof.sms.SemanticSchemaResult.NonFinancial ->
                            "review:non-financial:${variant.oldFamilyId ?: variant.id}"
                        is com.baraa.masroof.sms.SemanticSchemaResult.Ambiguous ->
                            "review:legacy:${semantic.reason}:${variant.oldFamilyId ?: variant.id}"
                    }
                    groupForVariant[variant.id] = SemanticGroup(variant.senderId, key)
                }

                val familyByGroup = linkedMapOf<SemanticGroup, Long>()
                groupForVariant.values.distinct().forEach { group ->
                    val members = variants.filter { groupForVariant[it.id] == group }
                    val status = when {
                        members.any { it.status == "APPROVED" } -> "APPROVED"
                        members.any { it.status == "UNKNOWN" } -> "UNKNOWN"
                        members.any { it.status == "IGNORED" } -> "IGNORED"
                        else -> "DEPRECATED"
                    }
                    val representative = members.minBy { it.id }
                    db.execSQL(
                        """
                        INSERT INTO message_pattern_families
                            (senderProfileId, stableKey, displayName, status, createdAt, updatedAt)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            group.senderId,
                            group.key,
                            representative.name.ifBlank { "نمط رسالة" },
                            status,
                            members.minOf { it.createdAt },
                            members.maxOf { it.updatedAt },
                        ),
                    )
                    val familyId = db.query(
                        """
                        SELECT id FROM message_pattern_families
                        WHERE senderProfileId = ? AND stableKey = ?
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf<Any>(group.senderId, group.key),
                    ).use { c -> check(c.moveToFirst()); c.getLong(0) }
                    familyByGroup[group] = familyId
                }

                variants.forEach { variant ->
                    val familyId = familyByGroup.getValue(groupForVariant.getValue(variant.id))
                    db.execSQL(
                        "UPDATE message_pattern_definitions SET familyId = ? WHERE id = ?",
                        arrayOf(familyId, variant.id),
                    )
                }
                db.execSQL(
                    "DELETE FROM message_pattern_families " +
                        "WHERE stableKey LIKE 'legacy-v29-family:%'",
                )
            }
        }

        /**
         * Re-key semantic families after introducing transaction-type-aware
         * salary and transfer identity. Definitions and their example counts
         * are preserved; only obsolete family metadata is consolidated.
         */
        val MIGRATION_30_31: Migration = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                data class Variant(
                    val id: Long,
                    val senderId: Long,
                    val oldFamilyId: Long?,
                    val name: String,
                    val status: String,
                    val template: String?,
                    val type: String?,
                    val createdAt: Long,
                    val updatedAt: Long,
                )

                val variants = mutableListOf<Variant>()
                db.query(
                    """
                    SELECT id, senderProfileId, familyId, userFriendlyName, status,
                           templateText, transactionType, createdAt, updatedAt
                    FROM message_pattern_definitions
                    ORDER BY id
                    """.trimIndent(),
                ).use { c ->
                    while (c.moveToNext()) {
                        variants += Variant(
                            id = c.getLong(0),
                            senderId = c.getLong(1),
                            oldFamilyId = if (c.isNull(2)) null else c.getLong(2),
                            name = c.getString(3).orEmpty(),
                            status = c.getString(4),
                            template = if (c.isNull(5)) null else c.getString(5),
                            type = if (c.isNull(6)) null else c.getString(6),
                            createdAt = c.getLong(7),
                            updatedAt = c.getLong(8),
                        )
                    }
                }

                db.execSQL(
                    "UPDATE message_pattern_families " +
                        "SET stableKey = 'legacy-v30-family:' || id",
                )

                data class SemanticGroup(val senderId: Long, val key: String)
                val groupForVariant = linkedMapOf<Long, SemanticGroup>()
                variants.forEach { variant ->
                    val semantic = com.baraa.masroof.sms.SemanticPatternSchemaNormalizer
                        .fromTemplate(variant.template, variant.type)
                    val key = when (semantic) {
                        is com.baraa.masroof.sms.SemanticSchemaResult.Safe -> semantic.key
                        is com.baraa.masroof.sms.SemanticSchemaResult.NonFinancial ->
                            "review:non-financial:${variant.oldFamilyId ?: variant.id}"
                        is com.baraa.masroof.sms.SemanticSchemaResult.Ambiguous ->
                            "review:legacy:${semantic.reason}:${variant.oldFamilyId ?: variant.id}"
                    }
                    groupForVariant[variant.id] = SemanticGroup(variant.senderId, key)
                }

                val familyByGroup = linkedMapOf<SemanticGroup, Long>()
                groupForVariant.values.distinct().forEach { group ->
                    val members = variants.filter { groupForVariant[it.id] == group }
                    val status = when {
                        members.any { it.status == "APPROVED" } -> "APPROVED"
                        members.any { it.status == "UNKNOWN" } -> "UNKNOWN"
                        members.any { it.status == "IGNORED" } -> "IGNORED"
                        else -> "DEPRECATED"
                    }
                    val representative = members.minBy { it.id }
                    db.execSQL(
                        """
                        INSERT INTO message_pattern_families
                            (senderProfileId, stableKey, displayName, status, createdAt, updatedAt)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        arrayOf(
                            group.senderId,
                            group.key,
                            representative.name.ifBlank { "نمط رسالة" },
                            status,
                            members.minOf { it.createdAt },
                            members.maxOf { it.updatedAt },
                        ),
                    )
                    val familyId = db.query(
                        """
                        SELECT id FROM message_pattern_families
                        WHERE senderProfileId = ? AND stableKey = ?
                        LIMIT 1
                        """.trimIndent(),
                        arrayOf<Any>(group.senderId, group.key),
                    ).use { c -> check(c.moveToFirst()); c.getLong(0) }
                    familyByGroup[group] = familyId
                }

                variants.forEach { variant ->
                    val familyId = familyByGroup.getValue(groupForVariant.getValue(variant.id))
                    db.execSQL(
                        "UPDATE message_pattern_definitions SET familyId = ? WHERE id = ?",
                        arrayOf(familyId, variant.id),
                    )
                }
                db.execSQL(
                    "DELETE FROM message_pattern_families " +
                        "WHERE stableKey LIKE 'legacy-v30-family:%'",
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
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
        )

        private fun semanticFamilyKey(
            transactionType: String?,
            direction: String?,
            channel: String?,
            templateText: String?,
        ): String {
            val structure = com.baraa.masroof.sms.CanonicalMessageNormalizer
                .normalizeTemplate(templateText)
            val type = com.baraa.masroof.transaction.TransactionTypeTaxonomy.parse(transactionType)
            val dir = com.baraa.masroof.transaction.TransactionTypeTaxonomy
                .parseDirection(direction, type)
            val instrument = com.baraa.masroof.sms.PatternStructure
                .detectPaymentInstrument(structure)
            return com.baraa.masroof.sms.PatternStructure.familyKey(
                transactionType = type,
                direction = dir,
                channel = channel,
                paymentInstrument = instrument,
            )
        }

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
