package com.baraa.masroof.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LedgerMigrationTest {
    private val source: String get() = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()

    @Test fun migrationSevenToEightIsAdditiveAndLeavesOldTransactionsUnposted() {
        assertTrue(source.contains("MIGRATION_7_8"))
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `journal_entries`"))
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `ledger_postings`"))
        assertTrue(source.contains("DEFAULT 'UNPOSTED'"))
        assertTrue(source.contains("systemAccountKey"))
    }

    @Test fun ledgerMigrationNeverDropsExistingData() {
        listOf("transactions", "categories", "merchant_memory", "ai_cache", "ai_settings").forEach { table ->
            assertFalse(Regex("DROP\\s+TABLE\\s+[`\"]?${Regex.escape(table)}[`\"]?").containsMatchIn(source))
        }
        assertTrue(
            "financial_accounts rebuild must copy rows before drop",
            source.contains("INSERT INTO `financial_accounts_new`") &&
                source.contains("FROM `financial_accounts`"),
        )
        assertFalse(Regex("""\.fallbackToDestructiveMigration\s*\(""").containsMatchIn(source))
    }
}
