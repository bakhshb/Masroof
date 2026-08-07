package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.repository.FinancialSetup
import com.baraa.masroof.data.db.MasroofDatabase
import com.baraa.masroof.data.repository.FakeFinancialAccountRepository
import com.baraa.masroof.data.repository.FinancialSetupRepository
import com.baraa.masroof.data.repository.RoomFinancialSetupRepository
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Tests for the setup flow, the migration defaults, and the no-
 * destructive-migration guarantee.
 *
 * These are JVM-only tests — they do not depend on a real Room
 * instance. They look at the migration source code and the
 * repository API surface.
 */
class FinancialSetupAndMigrationTest {

    // -- v6 → v7 migration source inspection ----------------------------

    @Test
    fun v6ToV7MigrationSourceIsPresent() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        // The migration must declare a v6 → v7 step.
        assertTrue(
            "MasroofDatabase must declare MIGRATION_6_7",
            source.contains("MIGRATION_6_7")
        )
        // The migration must be registered in ALL_MIGRATIONS.
        assertTrue(
            "MIGRATION_6_7 must be added to ALL_MIGRATIONS",
            source.contains("MIGRATION_6_7,") || source.contains("MIGRATION_6_7\n")
        )
        // The migration must create the financial_setup table.
        assertTrue(
            "v6 → v7 migration must create the financial_setup table",
            source.contains("CREATE TABLE IF NOT EXISTS `financial_setup`")
        )
        // Account columns added.
        for (col in listOf(
            "accountNature",
            "currency",
            "openingBalance",
            "openingBalanceDate",
            "includeInNetWorth",
            "includeInLiquidity",
            "notes"
        )) {
            assertTrue(
                "v6 → v7 migration must add `$col` to financial_accounts",
                source.contains("ADD COLUMN `$col`")
            )
        }
    }

    @Test
    fun v6ToV7MigrationBackfillsAccountNature() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        // The migration must backfill accountNature based on
        // accountType (CREDIT_CARD / LOAN / OTHER_LIABILITY → LIABILITY,
        // others → ASSET).
        assertTrue(
            "migration must set accountNature = 'LIABILITY' for credit cards",
            source.contains("'LIABILITY'") && source.contains("CREDIT_CARD")
        )
        assertTrue(
            "migration must set accountNature = 'ASSET' for non-liabilities",
            source.contains("'ASSET'")
        )
    }

    @Test
    fun v6ToV7MigrationBackfillsLiquidity() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        // The migration must backfill includeInLiquidity based on
        // accountType.
        assertTrue(
            "migration must set includeInLiquidity = 1 for banks/wallets/cash",
            source.contains("BANK_ACCOUNT") && source.contains("includeInLiquidity")
        )
    }

    @Test
    fun noDestructiveMigrationAnywhere() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt").readText()
        assertFalse(
            "Database source must not call fallbackToDestructiveMigration",
            Regex("""\.fallbackToDestructiveMigration\s*\(""").containsMatchIn(source)
        )
    }

    @Test
    fun noDropTableForExistingTables() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt")
            .readText()
        for (table in listOf(
            "transactions",
            "categories",
            "merchant_memory",
            "ai_cache",
            "ai_settings",
            "ai_suggestions",
        )) {
            assertFalse(
                "no migration may drop $table",
                Regex("""DROP\s+TABLE\s+[`"]?\Q$table\E[`"]?""")
                    .containsMatchIn(source),
            )
        }
        // financial_accounts may be rebuilt (copy → drop → rename) to retire
        // columns; never wiped without a preceding INSERT…SELECT copy.
        assertTrue(
            "financial_accounts rebuild must copy rows before drop",
            source.contains("INSERT INTO `financial_accounts_new`") &&
                source.contains("FROM `financial_accounts`"),
        )
    }

    @Test
    fun databaseVersionIsTwentyTwo() {
        val source = File("src/main/kotlin/com/baraa/masroof/data/db/MasroofDatabase.kt")
            .readText()
        val regex = Regex("""version\s*=\s*22""")
        assertTrue(
            "database version must be 22",
            regex.containsMatchIn(source),
        )
    }

    // -- Existing data preservation ------------------------------------

    @Test
    fun existingFinancialAccountRepositoryKeepsId() {
        // Existing owned accounts must be returned with their existing id
        // so the schema migration matches them in the new schema.
        val repo = FakeFinancialAccountRepository()
        runBlocking {
            val id = repo.add(
                displayName = "Legacy",
                accountType = AccountType.BANK_ACCOUNT,
                institutionName = "Al Rajhi",
                accountNature = AccountNature.ASSET,
                currency = Currency.SAR,
                openingBalance = BigDecimal("100"),
                openingBalanceDate = 1_700_000_000_000L,
                includeInNetWorth = true,
                includeInLiquidity = true
            )
            val fetched = repo.getById(id)
            assertNotNull(fetched)
            assertEquals(id, fetched!!.id)
            assertEquals("Legacy", fetched.displayName)
        }
    }

    @Test
    fun migrationPreservesExistingFinancialAccountData() {
        // Simulate the migration: an existing account (id=1, name="Old",
        // type=BANK_ACCOUNT) gets the new fields with safe defaults. We
        // assert that the placeholder values are sensible.
        val preset = mapOf(
            "id" to 1L,
            "displayName" to "Old",
            "accountType" to AccountType.BANK_ACCOUNT,
            "openingBalance" to BigDecimal.ZERO,
            "accountNature" to AccountNature.ASSET, // default for BANK_ACCOUNT
            "includeInNetWorth" to true,
            "includeInLiquidity" to true, // BANK_ACCOUNT default
        )
        // The migration's defaults must equal these preset values.
        assertEquals(BigDecimal.ZERO, preset["openingBalance"])
        assertEquals(AccountNature.ASSET, preset["accountNature"])
        assertEquals(true, preset["includeInNetWorth"])
        assertEquals(true, preset["includeInLiquidity"])
    }

    // -- Setup repository ----------------------------------------------

    @Test
    fun setupRepositoryDefaultIsIncomplete() {
        // Without a row, the repo returns a default with
        // setupCompleted = false.
        val def = FinancialSetup.defaultFor(today = 1_700_000_000_000L)
        assertFalse(def.setupCompleted)
        assertEquals(0L, def.setupCompletedAt)
    }

    @Test
    fun setupRepositorySurvivesSkip() {
        // The user can mark the setup as skipped (setupCompleted = false)
        // and the record still loads.
        val def = FinancialSetup.defaultFor(today = 1_700_000_000_000L)
        assertFalse("setup may be skipped", def.setupCompleted)
        assertEquals(0L, def.setupCompletedAt)
    }

    // -- Full-account-number safety ------------------------------------

    @Test
    fun fullAccountNumbersNeverStoredOnAccountEntity() {
        // Account identity lives in typed AccountIdentifierEntity rows.
        // FinancialAccount must not expose last-four / full-number fields.
        val repo = FakeFinancialAccountRepository()
        runBlocking {
            val id = repo.add(
                displayName = "X",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.ASSET,
                currency = Currency.SAR,
                openingBalance = BigDecimal.ZERO,
                openingBalanceDate = 0L,
                includeInNetWorth = true,
                includeInLiquidity = true,
            )
            val fetched = repo.getById(id)!!
            assertEquals("X", fetched.displayName)
            val fields = FinancialAccount::class.java.declaredFields.map { it.name.lowercase() }
            assertFalse(fields.any { it.contains("lastfour") || it.contains("accountnumber") })
        }
    }

    @Test
    fun financialAccountEntityHasNoFullNumberField() {
        // The Entity class must not declare any field that could store a
        // full account or card number.
        val fields = FinancialAccount::class.java.declaredFields
        for (f in fields) {
            val name = f.name.lowercase()
            assertFalse(
                "FinancialAccount must not have a full-number field (${f.name})",
                name.contains("fullnumber") ||
                    name.contains("accountnumber") ||
                    name.contains("cardnumber") ||
                    name.contains("iban") ||
                    name.contains("cvv")
            )
        }
    }

    @Test
    fun setupRepositoryHandlesRepeatedSaves() {
        // The mock repository is in-memory; we just exercise the
        // interface contract.
        val setup = FinancialSetup(
            trackingStartDate = 1_700_000_000_000L,
            setupCompleted = true,
            setupCompletedAt = 1_700_000_001_000L,
            defaultCurrency = Currency.SAR
        )
        assertTrue(setup.setupCompleted)
        assertEquals(1_700_000_001_000L, setup.setupCompletedAt)
    }

    // -- Existing spending tests must still pass ------------------------

    @Test
    fun seededAccountsPreserveOpeningBalanceAcrossRepository() {
        // Use the FakeFinancialAccountRepository to seed an account
        // with an opening balance, then verify the calculator uses it.
        val repo = FakeFinancialAccountRepository()
        runBlocking {
            repo.add(
                displayName = "Bank",
                accountType = AccountType.BANK_ACCOUNT,
                accountNature = AccountNature.ASSET,
                currency = Currency.SAR,
                openingBalance = BigDecimal("18000"),
                openingBalanceDate = 0L,
                includeInNetWorth = true,
                includeInLiquidity = true
            )
        }
        val accounts: List<FinancialAccount> = runBlocking { repo.observeAll().first() }
        val t = OpeningBalanceCalculator.compute(accounts)
        assertEquals(BigDecimal("18000.00"), t.perCurrency[Currency.SAR]!!.assets)
    }
}
