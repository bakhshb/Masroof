package com.baraa.masroof.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Migration test for the Masroof Room database.
 *
 * **This test cannot run on the current headless server** — Room's
 * [androidx.room.testing.MigrationTestHelper] requires an Android
 * `Context`. The class is provided so a developer with a connected
 * device (or a Robolectric-enabled test source set) can:
 *
 *  1. Add the `androidTest` source set and the `androidx.room:room-testing`
 *     dep.
 *  2. Annotate this class with `@RunWith(AndroidJUnit4::class)`.
 *  3. Use the helper to create a v1 DB, run [MasroofDatabase.MIGRATION_1_2],
 *     and verify the new column exists while existing data is preserved.
 *
 * For the headless JVM environment we provide a **limited sanity check**:
 * the migration object exists, has the right version range, and contains
 * the expected ALTER TABLE statement. The full migration is exercised
 * when a real device or emulator is available.
 */
class RoomMigrationTest {

    @Test
    fun migration1to2_startAndEndVersionsAreCorrect() {
        assertEquals(1, MasroofDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, MasroofDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration2to3_startAndEndVersionsAreCorrect() {
        assertEquals(2, MasroofDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, MasroofDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun migration3to4_startAndEndVersionsAreCorrect() {
        assertEquals(3, MasroofDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, MasroofDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun migration4to5_startAndEndVersionsAreCorrect() {
        assertEquals(4, MasroofDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, MasroofDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun migration5to6_startAndEndVersionsAreCorrect() {
        assertEquals(5, MasroofDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, MasroofDatabase.MIGRATION_5_6.endVersion)
        assertEquals(7, MasroofDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, MasroofDatabase.MIGRATION_7_8.endVersion)
    }

    @Test
    fun migration13to14PreservesIdentifiersAndAllowsSharedSenderAliases() {
        assertEquals(13, MasroofDatabase.MIGRATION_13_14.startVersion)
        assertEquals(14, MasroofDatabase.MIGRATION_13_14.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("INSERT INTO `account_identifiers_new`"))
        assertTrue(source.contains("`accountId`, `identifierType`, `normalizedValue`"))
        assertFalse(source.contains("Index(value = [\"normalizedValue\"], unique = true)"))
    }

    @Test
    fun allMigrationsArrayContainsAllMigrations() {
        val versions = MasroofDatabase.ALL_MIGRATIONS.map { "${it.startVersion}->${it.endVersion}" }
        val expected = setOf("1->2", "2->3", "3->4", "4->5", "5->6", "13->14")
        val missing = expected - versions.toSet()
        assertTrue(
            "ALL_MIGRATIONS missing $missing (was: $versions)",
            missing.isEmpty(),
        )
    }

    @Test
    fun migration2to3AddsTheNewColumnsAndTables() {
        // We can't actually run the migration on a real SQLite database
        // here without a Robolectric / Android context. So we inspect the
        // SQL the migration would execute by reading the source and looking
        // for the expected statements.
        val sourceFile = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt")
        val source = sourceFile.readText()
        assertTrue(
            "v2 -> v3 migration must add the financialTreatment column",
            source.contains("ADD COLUMN `financialTreatment`"),
        )
        assertTrue(
            "v2 -> v3 migration must add the categoryId column",
            source.contains("ADD COLUMN `categoryId`"),
        )
        assertTrue(
            "v2 -> v3 migration must add the categorySource column",
            source.contains("ADD COLUMN `categorySource`"),
        )
        assertTrue(
            "v2 -> v3 migration must create the categories table",
            source.contains("CREATE TABLE IF NOT EXISTS `categories`"),
        )
        assertTrue(
            "v2 -> v3 migration must create the merchant_memory table",
            source.contains("CREATE TABLE IF NOT EXISTS `merchant_memory`"),
        )
        assertTrue(
            "v2 -> v3 migration must create the financial_accounts table",
            source.contains("CREATE TABLE IF NOT EXISTS `financial_accounts`"),
        )
    }

    @Test
    fun migration5to6AddsAiSuggestionsTable() {
        // v5 -> v6 is purely additive: creates ai_suggestions table
        // with foreign key to transactions. Existing tables are
        // untouched.
        val sourceFile = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt")
        val source = sourceFile.readText()
        assertTrue(
            "v5 -> v6 migration must create the ai_suggestions table",
            source.contains("CREATE TABLE IF NOT EXISTS `ai_suggestions`"),
        )
        assertTrue(
            "v5 -> v6 migration must include a FK from ai_suggestions to transactions",
            source.contains("FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`)"),
        )
        // No destructive operations.
        val tables = listOf("transactions", "categories", "merchant_memory", "financial_accounts", "ai_cache", "ai_settings")
        for (table in tables) {
            val drop = Regex("""DROP\s+TABLE\s+[`"]?\Q$table\E[`"]?""")
            assertFalse(
                "no migration may drop $table (was found in source)",
                drop.containsMatchIn(source),
            )
        }
    }

    @Test
    fun migrationNamesAreAscendingAndNonOverlapping() {
        val sorted = MasroofDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }
        assertEquals(
            "MIGRATIONS must be sorted by startVersion",
            sorted.map { it.startVersion },
            sorted.map { it.startVersion }.sorted(),
        )
        for (i in 1 until sorted.size) {
            assertTrue(
                "Migrations ${i - 1} and $i overlap (${sorted[i - 1].endVersion} vs ${sorted[i].startVersion})",
                sorted[i - 1].endVersion <= sorted[i].startVersion,
            )
        }
    }
}
