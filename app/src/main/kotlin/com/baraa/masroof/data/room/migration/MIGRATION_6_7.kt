package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Bank hierarchy: bank_registry, credit_facility, loan_registry;
 * account types; credit facility ids on cards.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bank_registry` (
                `bankId` TEXT NOT NULL,
                `displayName` TEXT,
                PRIMARY KEY(`bankId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `credit_facility` (
                `id` TEXT NOT NULL,
                `bankId` TEXT NOT NULL,
                `primaryLast4` TEXT NOT NULL,
                `displayName` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_credit_facility_bankId`
            ON `credit_facility` (`bankId`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `loan_registry` (
                `id` TEXT NOT NULL,
                `bankId` TEXT NOT NULL,
                `loanType` TEXT NOT NULL,
                `ownershipStatus` TEXT NOT NULL,
                `displayName` TEXT,
                `firstSeenRawSmsId` TEXT,
                `lastSeenRawSmsId` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_loan_registry_bankId`
            ON `loan_registry` (`bankId`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `account_registry`
            ADD COLUMN `accountType` TEXT NOT NULL DEFAULT 'CURRENT'
            """.trimIndent(),
        )
        db.execSQL(
            """
            ALTER TABLE `card_registry`
            ADD COLUMN `creditFacilityId` TEXT
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO bank_registry (bankId)
            SELECT DISTINCT bankId FROM account_registry
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO bank_registry (bankId)
            SELECT DISTINCT bankId FROM card_registry
            """.trimIndent(),
        )

        migrateCreditFacilities(db)
    }

    private fun migrateCreditFacilities(db: SupportSQLiteDatabase) {
        data class CardRow(
            val bankId: String,
            val last4: String,
            val cardType: String?,
            val cardRole: String?,
            val parentCardLast4: String?,
        )

        val cards = mutableListOf<CardRow>()
        db.query("SELECT bankId, last4, cardType, cardRole, parentCardLast4 FROM card_registry").use { cursor ->
            val bankIdx = cursor.getColumnIndexOrThrow("bankId")
            val last4Idx = cursor.getColumnIndexOrThrow("last4")
            val typeIdx = cursor.getColumnIndexOrThrow("cardType")
            val roleIdx = cursor.getColumnIndexOrThrow("cardRole")
            val parentIdx = cursor.getColumnIndexOrThrow("parentCardLast4")
            while (cursor.moveToNext()) {
                cards.add(
                    CardRow(
                        bankId = cursor.getString(bankIdx),
                        last4 = cursor.getString(last4Idx),
                        cardType = if (cursor.isNull(typeIdx)) null else cursor.getString(typeIdx),
                        cardRole = if (cursor.isNull(roleIdx)) null else cursor.getString(roleIdx),
                        parentCardLast4 = if (cursor.isNull(parentIdx)) null else cursor.getString(parentIdx),
                    ),
                )
            }
        }

        fun isCredit(card: CardRow): Boolean = card.cardType != "DEBIT"

        val creditCards = cards.filter { isCredit(it) }
        val primaryEntries = creditCards.filter { it.cardRole == "PRIMARY" }
        val supplementaryEntries = creditCards.filter { it.cardRole == "SUPPLEMENTARY" }
        val standaloneEntries = creditCards.filter {
            it.cardRole == "STANDALONE" || it.cardRole == null
        }

        fun facilityId(bankId: String, primaryLast4: String): String =
            "facility:$bankId:$primaryLast4"

        fun insertFacility(bankId: String, primaryLast4: String) {
            val id = facilityId(bankId, primaryLast4)
            db.execSQL(
                "INSERT OR IGNORE INTO credit_facility (id, bankId, primaryLast4) VALUES (?, ?, ?)",
                arrayOf(id, bankId, primaryLast4),
            )
        }

        fun setCardFacility(bankId: String, last4: String, primaryLast4: String) {
            val id = facilityId(bankId, primaryLast4)
            db.execSQL(
                "UPDATE card_registry SET creditFacilityId = ? WHERE bankId = ? AND last4 = ?",
                arrayOf(id, bankId, last4),
            )
        }

        for (primary in primaryEntries) {
            insertFacility(primary.bankId, primary.last4)
            setCardFacility(primary.bankId, primary.last4, primary.last4)
            for (supplement in supplementaryEntries.filter { it.parentCardLast4 == primary.last4 }) {
                setCardFacility(supplement.bankId, supplement.last4, primary.last4)
            }
        }

        for (standalone in standaloneEntries) {
            insertFacility(standalone.bankId, standalone.last4)
            setCardFacility(standalone.bankId, standalone.last4, standalone.last4)
        }

        val groupedLast4s = (primaryEntries.map { it.last4 } + standaloneEntries.map { it.last4 }).toSet()
        val orphanSupplements = supplementaryEntries.filter { it.parentCardLast4 !in groupedLast4s }
        for (orphan in orphanSupplements) {
            insertFacility(orphan.bankId, orphan.last4)
            setCardFacility(orphan.bankId, orphan.last4, orphan.last4)
        }
    }
}
