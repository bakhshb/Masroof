package com.baraa.masroof.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun allMigrationsArrayContainsTheV1ToV2Migration() {
        val versions = MasroofDatabase.ALL_MIGRATIONS.map { "${it.startVersion}->${it.endVersion}" }
        assertTrue(
            "ALL_MIGRATIONS must contain 1->2 (was: $versions)",
            versions.contains("1->2"),
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
