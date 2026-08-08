package com.baraa.masroof.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration29To30InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroofDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun equivalentVariantsMergeIntoOneSemanticFamilyWithoutDeletingRows() {
        helper.createDatabase(DB, 29).apply {
            execSQL(
                "INSERT INTO sender_profiles VALUES (1,'BankX','bankx',NULL,NULL,1,0,0)",
            )
            execSQL(
                "INSERT INTO message_pattern_families VALUES (1,1,'old-a','POS A','APPROVED',0,0)",
            )
            execSQL(
                "INSERT INTO message_pattern_families VALUES (2,1,'old-b','POS B','UNKNOWN',0,0)",
            )
            insertVariant(
                id = 1,
                familyId = 1,
                signature = "a",
                template = "شراء عبر نقاط البيع\nلدى: {MERCHANT}\nبمبلغ: {AMOUNT} SAR\nبطاقة مدى: {DEBIT_CARD_LAST4}",
                count = 28,
                status = "APPROVED",
            )
            insertVariant(
                id = 2,
                familyId = 2,
                signature = "b",
                template = "شراء عبر نقاط البيع\nالتاجر: {MERCHANT}\nالمبلغ: {AMOUNT} SAR\nبطاقة مدى رقم: {DEBIT_CARD_LAST4}",
                count = 16,
                status = "UNKNOWN",
            )
            close()
        }

        helper.runMigrationsAndValidate(DB, 30, true, MasroofDatabase.MIGRATION_29_30).apply {
            val familyCount = query("SELECT COUNT(*) FROM message_pattern_families").use {
                it.moveToFirst()
                it.getLong(0)
            }
            val variantCount = query("SELECT COUNT(*) FROM message_pattern_definitions").use {
                it.moveToFirst()
                it.getLong(0)
            }
            val totalExamples = query("SELECT SUM(exampleCount) FROM message_pattern_definitions").use {
                it.moveToFirst()
                it.getLong(0)
            }
            val key = query("SELECT stableKey FROM message_pattern_families").use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals(1L, familyCount)
            assertEquals(2L, variantCount)
            assertEquals(44L, totalExamples)
            assertTrue(key.startsWith("semantic-v1|PURCHASE|OUTFLOW|DEBIT_CARD|"))
            close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertVariant(
        id: Long,
        familyId: Long,
        signature: String,
        template: String,
        count: Int,
        status: String,
    ) {
        execSQL(
            """
            INSERT INTO message_pattern_definitions (
                id,senderProfileId,familyId,userFriendlyName,normalizedSignature,canonicalKey,
                lineageId,templateText,transactionType,direction,channel,status,version,isActive,
                origin,confidence,userConfirmed,exampleCount,normalizationVersion,activeFrom,
                deprecatedAt,createdAt,updatedAt
            ) VALUES (?,?,?,'شراء عبر نقاط البيع',?,?,?,?,'PURCHASE','OUTFLOW',NULL,?,1,?,
                      'USER_TRAINED',100,1,?,2,NULL,NULL,0,0)
            """.trimIndent(),
            arrayOf(
                id,
                1L,
                familyId,
                signature,
                signature,
                id,
                template,
                status,
                if (status == "APPROVED") 1 else 0,
                count,
            ),
        )
    }

    private companion object {
        const val DB = "migration-test-30.db"
    }
}
