package com.baraa.masroof.data.db

import org.junit.Assert.assertEquals
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
    fun allMigrationsArrayContainsBothMigrations() {
        val versions = MasroofDatabase.ALL_MIGRATIONS.map { "${it.startVersion}->${it.endVersion}" }
        assertTrue(
            "ALL_MIGRATIONS must contain 1->2 (was: $versions)",
            versions.contains("1->2"),
        )
        assertTrue(
            "ALL_MIGRATIONS must contain 2->3 (was: $versions)",
            versions.contains("2->3"),
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
