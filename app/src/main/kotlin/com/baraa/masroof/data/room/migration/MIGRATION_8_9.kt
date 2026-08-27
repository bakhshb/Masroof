package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.OwnershipStatus

/**
 * Loan registry: composite key (bankId, loanType), stable ids, dedupe legacy rows.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        data class LoanRow(
            val id: String,
            val bankId: String,
            val loanType: String,
            val ownershipStatus: String,
            val displayName: String?,
            val firstSeenRawSmsId: String?,
            val lastSeenRawSmsId: String?,
        )

        val rows = buildList {
            db.query("SELECT id, bankId, loanType, ownershipStatus, displayName, firstSeenRawSmsId, lastSeenRawSmsId FROM loan_registry")
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        add(
                            LoanRow(
                                id = cursor.getString(0),
                                bankId = cursor.getString(1),
                                loanType = cursor.getString(2),
                                ownershipStatus = cursor.getString(3),
                                displayName = cursor.getString(4),
                                firstSeenRawSmsId = cursor.getString(5),
                                lastSeenRawSmsId = cursor.getString(6),
                            ),
                        )
                    }
                }
        }

        db.execSQL("DROP TABLE IF EXISTS loan_registry")
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
                PRIMARY KEY(`bankId`, `loanType`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_registry_bankId_loanType`
            ON `loan_registry` (`bankId`, `loanType`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_registry_id`
            ON `loan_registry` (`id`)
            """.trimIndent(),
        )

        rows.groupBy { it.bankId to it.loanType }
            .values
            .forEach { group ->
                val winner = group.maxWith(
                    compareBy<LoanRow> { ownershipRank(it.ownershipStatus) }
                        .thenBy { it.lastSeenRawSmsId.orEmpty() },
                )
                val stableId = RegistryEntityIdFactory.stableLoanId(winner.bankId, winner.loanType)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO loan_registry (
                        id, bankId, loanType, ownershipStatus, displayName,
                        firstSeenRawSmsId, lastSeenRawSmsId
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        stableId,
                        winner.bankId,
                        winner.loanType,
                        winner.ownershipStatus,
                        winner.displayName,
                        group.mapNotNull { it.firstSeenRawSmsId }.minOrNull(),
                        group.mapNotNull { it.lastSeenRawSmsId }.maxOrNull(),
                    ),
                )
            }
    }

    private fun ownershipRank(status: String): Int =
        when (status) {
            OwnershipStatus.OWNED.name -> 2
            OwnershipStatus.UNKNOWN.name -> 1
            else -> 0
        }
}
