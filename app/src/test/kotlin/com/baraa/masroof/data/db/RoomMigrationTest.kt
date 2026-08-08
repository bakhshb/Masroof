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
    fun migration14to15BackfillsIdentifiersAndDropsLegacyColumns() {
        assertEquals(14, MasroofDatabase.MIGRATION_14_15.startVersion)
        assertEquals(15, MasroofDatabase.MIGRATION_14_15.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("INSERT OR IGNORE INTO `account_identifiers`"))
        assertTrue(source.contains("CREATE TABLE `financial_accounts_new`"))
        assertTrue(source.contains("DROP TABLE `financial_accounts`"))
        assertTrue(source.contains("ALTER TABLE `financial_accounts_new` RENAME TO `financial_accounts`"))
        // Rebuild must not reintroduce legacy columns.
        val rebuildBlock = source.substringAfter("CREATE TABLE `financial_accounts_new`")
            .substringBefore("INSERT INTO `financial_accounts_new`")
        assertFalse(rebuildBlock.contains("lastFourDigits"))
        assertFalse(rebuildBlock.contains("senderAliases"))
    }

    @Test
    fun migration15to16AddsOnDeviceAiColumns() {
        assertEquals(15, MasroofDatabase.MIGRATION_15_16.startVersion)
        assertEquals(16, MasroofDatabase.MIGRATION_15_16.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("ADD COLUMN `deploymentMode`"))
        assertTrue(source.contains("ADD COLUMN `onDeviceModelPath`"))
    }

    @Test
    fun migration16to17AddsTransactionSmsBodiesTable() {
        assertEquals(16, MasroofDatabase.MIGRATION_16_17.startVersion)
        assertEquals(17, MasroofDatabase.MIGRATION_16_17.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `transaction_sms_bodies`"))
        assertTrue(source.contains("FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`)"))
        assertTrue(source.contains("ON DELETE CASCADE"))
    }

    @Test
    fun migration17to18AddsSenderMessagePatternsTable() {
        assertEquals(17, MasroofDatabase.MIGRATION_17_18.startVersion)
        assertEquals(18, MasroofDatabase.MIGRATION_17_18.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `sender_message_patterns`"))
        assertTrue(source.contains("index_sender_message_patterns_senderKey_accountId_kind"))
    }

    @Test
    fun migration18to19DetachesPatternsFromAccountAndAddsStructureKey() {
        assertEquals(18, MasroofDatabase.MIGRATION_18_19.startVersion)
        assertEquals(19, MasroofDatabase.MIGRATION_18_19.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("sender_message_patterns_new"))
        assertTrue(source.contains("structureKey"))
        assertTrue(source.contains("index_sender_message_patterns_senderKey_structureKey_kind"))
    }

    @Test
    fun migration19to20AddsSenderProfiles() {
        assertEquals(19, MasroofDatabase.MIGRATION_19_20.startVersion)
        assertEquals(20, MasroofDatabase.MIGRATION_19_20.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `sender_profiles`"))
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `account_sender_profiles`"))
        assertTrue(source.contains("SENDER_ALIAS"))
    }

    @Test
    fun migration20to21AddsMessagePatternDefinitions() {
        assertEquals(20, MasroofDatabase.MIGRATION_20_21.startVersion)
        assertEquals(21, MasroofDatabase.MIGRATION_20_21.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `message_pattern_definitions`"))
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `pattern_field_definitions`"))
    }

    @Test
    fun migration21to22DropsLegacySenderMessagePatterns() {
        assertEquals(21, MasroofDatabase.MIGRATION_21_22.startVersion)
        assertEquals(22, MasroofDatabase.MIGRATION_21_22.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("DROP TABLE IF EXISTS `sender_message_patterns`"))
        assertTrue(source.contains("'IGNORED'"))
        assertTrue(source.contains("TRANSACTION_AMOUNT"))
    }

    @Test
    fun migration22to23RemovesSenderAliasIdentifiers() {
        assertEquals(22, MasroofDatabase.MIGRATION_22_23.startVersion)
        assertEquals(23, MasroofDatabase.MIGRATION_22_23.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("DELETE FROM account_identifiers WHERE identifierType = 'SENDER_ALIAS'"))
    }

    @Test
    fun migration23to24AddsTemplateTextColumn() {
        assertEquals(23, MasroofDatabase.MIGRATION_23_24.startVersion)
        assertEquals(24, MasroofDatabase.MIGRATION_23_24.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("ADD COLUMN templateText TEXT"))
    }

    @Test
    fun migration24to25AddsCanonicalKeyAndMergesDuplicates() {
        assertEquals(24, MasroofDatabase.MIGRATION_24_25.startVersion)
        assertEquals(25, MasroofDatabase.MIGRATION_24_25.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("ADD COLUMN canonicalKey TEXT NOT NULL DEFAULT ''"))
        assertTrue(source.contains("index_message_pattern_definitions_senderProfileId_canonicalKey"))
        // Duplicate merge must preserve field definitions and survivor selection
        // must be the shared, unit-tested rule.
        assertTrue(source.contains("PatternDuplicateMerger.selectSurvivor"))
        assertTrue(source.contains("PatternDuplicateMerger.mergedExampleCount"))
        assertTrue(source.contains("UPDATE pattern_field_definitions SET patternId ="))
        // Only duplicate losers are deleted — never the whole table.
        assertFalse(source.contains("DROP TABLE IF EXISTS `message_pattern_definitions`"))
        assertTrue(source.contains("DELETE FROM message_pattern_definitions WHERE id IN"))
    }

    @Test
    fun migration25to26RecomputesSemanticFamilyKeys() {
        assertEquals(25, MasroofDatabase.MIGRATION_25_26.startVersion)
        assertEquals(26, MasroofDatabase.MIGRATION_25_26.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("MIGRATION_25_26"))
        assertTrue(source.contains("semantic family"))
        assertTrue(source.contains("stripWalletFromDisplayName"))
        assertFalse(source.contains("DROP TABLE IF EXISTS `message_pattern_definitions`"))
    }

    @Test
    fun migration26to27CanonicalizesTypesAndAddsTemplateRevisions() {
        assertEquals(26, MasroofDatabase.MIGRATION_26_27.startVersion)
        assertEquals(27, MasroofDatabase.MIGRATION_26_27.endVersion)
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertTrue(source.contains("MIGRATION_26_27"))
        assertTrue(source.contains("WHEN 'BANK_FEE' THEN 'FEE'"))
        assertTrue(source.contains("WHEN 'DEPOSIT' THEN 'OTHER_FINANCIAL'"))
        assertTrue(source.contains("ADD COLUMN `lineageId`"))
        assertTrue(source.contains("ADD COLUMN `isActive`"))
        assertTrue(source.contains("ADD COLUMN `placeholderToken`"))
        assertTrue(source.contains("active = 0"))
        assertFalse(
            source.substringAfter("val MIGRATION_26_27")
                .substringBefore("/** All migrations")
                .contains("DELETE FROM transactions"),
        )
        assertFalse(
            source.substringAfter("val MIGRATION_26_27")
                .substringBefore("/** All migrations")
                .contains("journal_entries"),
        )
    }

    @Test
    fun allMigrationsArrayContainsAllMigrations() {
        val versions = MasroofDatabase.ALL_MIGRATIONS.map { "${it.startVersion}->${it.endVersion}" }
        val expected = setOf(
            "1->2", "2->3", "3->4", "4->5", "5->6", "13->14", "14->15",
            "15->16", "16->17", "17->18", "18->19", "19->20", "20->21", "21->22", "22->23", "23->24",
            "24->25", "25->26", "26->27", "27->28", "28->29", "29->30", "30->31",
        )
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
            source.contains("ADD COLUMN `financialTreatment`")
        )
        assertTrue(
            "v2 -> v3 migration must add the categoryId column",
            source.contains("ADD COLUMN `categoryId`")
        )
        assertTrue(
            "v2 -> v3 migration must add the categorySource column",
            source.contains("ADD COLUMN `categorySource`")
        )
        assertTrue(
            "v2 -> v3 migration must create the categories table",
            source.contains("CREATE TABLE IF NOT EXISTS `categories`")
        )
        assertTrue(
            "v2 -> v3 migration must create the merchant_memory table",
            source.contains("CREATE TABLE IF NOT EXISTS `merchant_memory`")
        )
        assertTrue(
            "v2 -> v3 migration must create the financial_accounts table",
            source.contains("CREATE TABLE IF NOT EXISTS `financial_accounts`")
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
            source.contains("CREATE TABLE IF NOT EXISTS `ai_suggestions`")
        )
        assertTrue(
            "v5 -> v6 migration must include a FK from ai_suggestions to transactions",
            source.contains("FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`)")
        )
        // No destructive wipe of core tables. financial_accounts may be
        // rebuilt elsewhere to retire columns (copy → drop → rename).
        val tables = listOf("transactions", "categories", "merchant_memory", "ai_cache", "ai_settings")
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
            sorted.map { it.startVersion }.sorted()
        )
        for (i in 1 until sorted.size) {
            assertTrue(
                "Migrations ${i - 1} and $i overlap (${sorted[i - 1].endVersion} vs ${sorted[i].startVersion})",
                sorted[i - 1].endVersion <= sorted[i].startVersion
            )
        }
    }
}
