package com.baraa.masroof.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration30To31InstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroofDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun salaryRoutingVariantsMergeWithoutDeletingDefinitionsOrCounts() {
        helper.createDatabase(DB, 30).apply {
            execSQL("INSERT INTO sender_profiles VALUES (1,'BankX','bankx',NULL,NULL,1,0,0)")
            execSQL(
                "INSERT INTO message_pattern_families VALUES " +
                    "(1,1,'semantic-v1|salary-beneficiary','Salary A','APPROVED',0,0)",
            )
            execSQL(
                "INSERT INTO message_pattern_families VALUES " +
                    "(2,1,'semantic-v1|salary-account','Salary B','UNKNOWN',0,0)",
            )
            insertSalaryVariant(
                id = 1,
                familyId = 1,
                template = "حوالة واردة راتب\nمبلغ: {AMOUNT} SAR\nاسم المرسل: {BENEFICIARY}",
                count = 9,
                status = "APPROVED",
            )
            insertSalaryVariant(
                id = 2,
                familyId = 2,
                template = "حوالة واردة راتب\nمبلغ: {AMOUNT} SAR\nإلى حساب: {ACCOUNT_LAST4}",
                count = 4,
                status = "UNKNOWN",
            )
            close()
        }

        helper.runMigrationsAndValidate(DB, 31, true, MasroofDatabase.MIGRATION_30_31).apply {
            val familyCount = query("SELECT COUNT(*) FROM message_pattern_families").use {
                it.moveToFirst()
                it.getLong(0)
            }
            val definitionCount = query("SELECT COUNT(*) FROM message_pattern_definitions").use {
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
            assertEquals(2L, definitionCount)
            assertEquals(13L, totalExamples)
            assertTrue(key.startsWith("semantic-v2|SALARY|INFLOW|UNKNOWN|"))
            close()
        }
    }

    private fun SupportSQLiteDatabase.insertSalaryVariant(
        id: Long,
        familyId: Long,
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
            ) VALUES (?,1,?,'راتب',?,?,?,?,'SALARY','INFLOW',NULL,?,1,?,
                      'USER_TRAINED',100,1,?,2,NULL,NULL,0,0)
            """.trimIndent(),
            arrayOf(
                id,
                familyId,
                "salary-$id",
                "salary-$id",
                id,
                template,
                status,
                if (status == "APPROVED") 1 else 0,
                count,
            ),
        )
    }

    private companion object {
        const val DB = "migration-test-31.db"
    }
}
