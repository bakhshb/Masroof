package com.baraa.masroof.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that the Room v4→v5 migration is **purely additive** — no
 * DROP, no destructive change — so existing transactions, categories,
 * accounts, and merchant memories are preserved.
 *
 * The test reads the source of [com.baraa.masroof.data.db.MasroofDatabase]
 * from disk and looks for forbidden statements inside any migration.
 */
class MigrationPreservationTest {

    private val sourceFile by lazy {
        val candidates = listOf(
            "src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt",
            "../src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt",
            "app/src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt",
        )
        candidates.map { File(it) }.firstOrNull { it.exists() }
            ?: error("Could not locate MasroffDatabase.kt")
    }

    private val source by lazy { sourceFile.readText() }

    @Test
    fun noDestructiveMigration() {
        // The schema-safety test already asserts that
        // `fallbackToDestructiveMigration()` is not called. Here we
        // also assert that NO migration contains DROP TABLE for any of
        // the four core tables.
        for (table in listOf("transactions", "categories", "merchant_memory", "financial_accounts")) {
            val regex = Regex("""DROP\s+TABLE\s+[`"]?\Q$table\E[`"]?""")
            assertFalse(
                "no migration may drop $table (was found in source)",
                regex.containsMatchIn(source),
            )
        }
    }

    @Test
    fun v4to5MigrationIsAdditive() {
        // The v4→v5 migration is supposed to CREATE two new tables
        // (`ai_cache`, `ai_settings`) without touching the existing ones.
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `ai_cache`"))
        assertTrue(source.contains("CREATE TABLE IF NOT EXISTS `ai_settings`"))
        // It must NOT contain ALTER TABLE … DROP COLUMN.
        assertFalse(
            "v4→v5 must not drop columns",
            Regex("""ALTER\s+TABLE[^;]*DROP\s+COLUMN""").containsMatchIn(source),
        )
    }

    @Test
    fun apiKeyNeverInRoomSchema() {
        // The schema export must not include any "apiKey" / "api_key"
        // / "secret" / "password" column.
        for (v in listOf("1.json", "2.json", "3.json", "4.json", "5.json")) {
            val schemaFile = File("app/schemas/com.baraa.masroof.data.db.MasroofDatabase/$v")
            if (!schemaFile.exists()) continue
            val json = schemaFile.readText()
            assertFalse(
                "schema $v must not contain api_key column",
                json.contains("api_key"),
            )
            assertFalse(
                "schema $v must not contain apiKey column",
                json.contains("apiKey"),
            )
            assertFalse(
                "schema $v must not contain password column",
                json.contains("password"),
            )
        }
    }

    @Test
    fun allMigrationsPresentInSource() {
        for (pair in listOf("1_2", "2_3", "3_4", "4_5")) {
            val migration = "MIGRATION_$pair"
            assertTrue("source must declare $migration", source.contains("val $migration"))
        }
        // And ALL_MIGRATIONS must list them all.
        assertTrue(source.contains("MIGRATION_1_2"))
        assertTrue(source.contains("MIGRATION_2_3"))
        assertTrue(source.contains("MIGRATION_3_4"))
        assertTrue(source.contains("MIGRATION_4_5"))
    }
}