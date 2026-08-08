package com.baraa.masroof.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration26To27InstrumentedTest {
    private val dbName = "migration-26-27"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroofDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun canonicalizesTypesAndRetiresSignatureOnlyPatternsWithoutTouchingTreatment() {
        helper.createDatabase(dbName, 26).apply {
            execSQL(
                """
                INSERT INTO sender_profiles
                (id, displaySender, normalizedSenderKey, institutionId,
                 displayInstitutionName, active, createdAt, updatedAt)
                VALUES (1, 'TESTBANK', 'testbank', NULL, 'Test', 1, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO message_pattern_definitions
                (id, senderProfileId, userFriendlyName, normalizedSignature,
                 canonicalKey, templateText, transactionType, direction, channel,
                 status, version, origin, confidence, userConfirmed, exampleCount,
                 activeFrom, deprecatedAt, createdAt, updatedAt)
                VALUES
                (1, 1, 'fee', 'sig-fee', 'old-fee', 'رسوم\nالمبلغ: {AMOUNT}',
                 'BANK_FEE', 'OUT', 'SMS', 'APPROVED', 1, 'MIGRATED', 90, 1, 2,
                 10, NULL, 10, 10),
                (2, 1, 'old signature', 'sig-only', 'old-signature', NULL,
                 'DEPOSIT', 'IN', 'SMS', 'APPROVED', 1, 'MIGRATED', 50, 1, 1,
                 10, NULL, 10, 10)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transactions
                (id, uniqueFingerprint, smsTimestamp, originalSender, transactionType,
                 amount, currency, merchantOrBeneficiary, accountOrCardLastFourDigits,
                 transactionDate, transactionTime, status, confidence, parsingNotes,
                 dateSource, createdAt, updatedAt, transactionSimilarityKey,
                 financialTreatment, categoryId, categorySource, categoryConfidence,
                 needsReview, userConfirmed, exclusionReason, sourceAccountId,
                 destinationAccountId, linkedJournalEntryId, accountLinkSource,
                 accountLinkConfidence, accountLinkNeedsReview, postingStatus)
                VALUES
                (1, 'fingerprint-kept', 100, 'TESTBANK', 'BANK_FEE', '5.00', 'SAR',
                 NULL, NULL, NULL, NULL, 'COMPLETED', 90, '', 'FROM_SMS_METADATA',
                 100, 100, 'similarity-kept', 'EXPENSE', NULL, 'UNCLASSIFIED', 0,
                 0, 1, NULL, NULL, NULL, NULL, 'UNLINKED', 0, 1, 'NOT_POSTED')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            27,
            true,
            MasroofDatabase.MIGRATION_26_27,
        )
        db.query(
            "SELECT transactionType, financialTreatment, uniqueFingerprint, transactionSimilarityKey " +
                "FROM transactions WHERE id = 1",
        ).use {
            it.moveToFirst()
            assertEquals("FEE", it.getString(0))
            assertEquals("EXPENSE", it.getString(1))
            assertEquals("fingerprint-kept", it.getString(2))
            assertEquals("similarity-kept", it.getString(3))
        }
        db.query(
            "SELECT transactionType, direction, isActive, lineageId FROM message_pattern_definitions WHERE id = 1",
        ).use {
            it.moveToFirst()
            assertEquals("FEE", it.getString(0))
            assertEquals("OUTFLOW", it.getString(1))
            assertEquals(1, it.getInt(2))
            assertEquals(1L, it.getLong(3))
        }
        db.query(
            "SELECT transactionType, direction, isActive FROM message_pattern_definitions WHERE id = 2",
        ).use {
            it.moveToFirst()
            assertEquals("OTHER_FINANCIAL", it.getString(0))
            assertEquals("INFLOW", it.getString(1))
            assertFalse(it.getInt(2) != 0)
        }
        db.close()
    }
}
