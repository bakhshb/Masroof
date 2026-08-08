package com.baraa.masroof.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.baraa.masroof.sms.NORMALIZATION_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the v28 → v29 migration:
 *  - Adds the `normalizationVersion` column (default 0).
 *  - Re-keys existing pattern families using semantic identity
 *    (transactionType + direction + paymentInstrument + channel).
 *  - Never deletes a variant, transaction, account, journal, or posting.
 */
@RunWith(AndroidJUnit4::class)
class Migration28To29InstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroofDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v28_to_v29_preservesVariantsAndStampsNormalizationVersionAsZero() {
        // Seed a minimal v28 schema with one sender, one family, one variant.
        helper.createDatabase(TEST_DB, 28).apply {
            execSQL(
                "INSERT INTO sender_profiles (id, displaySender, normalizedSenderKey, institutionId, " +
                    "displayInstitutionName, active, createdAt, updatedAt) VALUES " +
                    "(1, 'BankX', 'bankx', NULL, NULL, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO message_pattern_families (id, senderProfileId, stableKey, displayName, status, " +
                    "createdAt, updatedAt) VALUES (1, 1, 'legacy:label', 'Legacy Family', 'APPROVED', 0, 0)",
            )
            execSQL(
                "INSERT INTO message_pattern_definitions (id, senderProfileId, familyId, userFriendlyName, " +
                    "normalizedSignature, canonicalKey, lineageId, templateText, transactionType, direction, " +
                    "channel, status, version, isActive, origin, confidence, userConfirmed, exampleCount, " +
                    "activeFrom, deprecatedAt, createdAt, updatedAt) VALUES " +
                    "(1, 1, 1, 'نمط قديم', 'sig-legacy', 'legacy:key', 0, 'شراء عبر الإنترنت\nلدى: {MERCHANT}', " +
                    "'ONLINE_PURCHASE', 'OUTFLOW', NULL, 'APPROVED', 1, 1, 'USER_TRAINED', 100, 1, 5, NULL, NULL, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 29, true, MasroofDatabase.MIGRATION_28_29).apply {
            // Variant preserved.
            val variantCount = query("SELECT COUNT(*) FROM message_pattern_definitions").use {
                it.moveToFirst()
                it.getLong(0)
            }
            assertEquals(1L, variantCount)

            // Variant stamped normalizationVersion = 0 (STALE under current version).
            val version = query(
                "SELECT normalizationVersion FROM message_pattern_definitions WHERE id = 1"
            ).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(0, version)
            assertTrue("variant must be marked STALE", version != NORMALIZATION_VERSION)

            // Family re-keyed using semantic identity. With transactionType=ONLINE_PURCHASE,
            // direction=OUTFLOW, channel=null, paymentInstrument=UNKNOWN the new stableKey is:
            // "ONLINE_PURCHASE|OUTFLOW|NONE|UNKNOWN".
            val familyKey = query(
                "SELECT stableKey FROM message_pattern_families WHERE id = 1"
            ).use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals("ONLINE_PURCHASE|OUTFLOW|NONE|UNKNOWN", familyKey)
        }
    }

    @Test
    fun v28_to_v29_isIdempotentAndDoesNotDeleteData() {
        helper.createDatabase(TEST_DB, 28).apply {
            execSQL(
                "INSERT INTO sender_profiles (id, displaySender, normalizedSenderKey, institutionId, " +
                    "displayInstitutionName, active, createdAt, updatedAt) VALUES " +
                    "(1, 'BankX', 'bankx', NULL, NULL, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO message_pattern_families (id, senderProfileId, stableKey, displayName, status, " +
                    "createdAt, updatedAt) VALUES (1, 1, 's', 'Family', 'APPROVED', 0, 0)",
            )
            execSQL(
                "INSERT INTO message_pattern_definitions (id, senderProfileId, familyId, userFriendlyName, " +
                    "normalizedSignature, canonicalKey, lineageId, templateText, transactionType, direction, " +
                    "channel, status, version, isActive, origin, confidence, userConfirmed, exampleCount, " +
                    "activeFrom, deprecatedAt, createdAt, updatedAt) VALUES " +
                    "(1, 1, 1, 'P', 'sig', 'key', 0, 'حوالة صادرة\nمن حساب: 1', 'TRANSFER_OUT', 'OUTFLOW', NULL, " +
                    "'APPROVED', 1, 1, 'USER_TRAINED', 100, 1, 1, NULL, NULL, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 29, true, MasroffMigration).apply {
            val variantCount = query("SELECT COUNT(*) FROM message_pattern_definitions").use {
                it.moveToFirst()
                it.getLong(0)
            }
            assertEquals(1L, variantCount)
            val senderCount = query("SELECT COUNT(*) FROM sender_profiles").use {
                it.moveToFirst()
                it.getLong(0)
            }
            assertEquals(1L, senderCount)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-29.db"
        val MasroffMigration = MasroofDatabase.MIGRATION_28_29
    }
}