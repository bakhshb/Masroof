package com.baraa.masroof.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration27To28InstrumentedTest {
    private val dbName = "migration-27-28"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroofDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun preservesPatternsAndFieldsWhileBackfillingOneFamilyWithVariants() {
        helper.createDatabase(dbName, 27).apply {
            execSQL("INSERT INTO sender_profiles (id,displaySender,normalizedSenderKey,institutionId,displayInstitutionName,active,createdAt,updatedAt) VALUES (1,'TESTBANK','testbank',NULL,NULL,1,1,1)")
            execSQL("""
                INSERT INTO message_pattern_definitions
                (id,senderProfileId,userFriendlyName,normalizedSignature,canonicalKey,lineageId,templateText,transactionType,direction,channel,status,version,isActive,origin,confidence,userConfirmed,exampleCount,activeFrom,deprecatedAt,createdAt,updatedAt)
                VALUES
                (1,1,'تحويل صادر','title=تحويل صادر|من حساب=<FOUR_DIGIT_VALUE>','variant:one',1,'تحويل صادر\nمن حساب: {ACCOUNT_LAST4}','TRANSFER_OUT',NULL,NULL,'APPROVED',1,1,'MIGRATED',100,1,19,1,NULL,1,1),
                (2,1,'تحويل صادر','title=تحويل صادر|المستفيد=<VARIABLE_TEXT>','variant:two',2,'تحويل صادر\nالمستفيد: {BENEFICIARY}','TRANSFER_OUT',NULL,NULL,'APPROVED',1,1,'MIGRATED',100,1,36,1,NULL,1,1)
            """.trimIndent())
            execSQL("INSERT INTO pattern_field_definitions (id,patternId,canonicalField,placeholderToken,sourceLabel,extractionStrategy,required,role,valueType) VALUES (1,1,'ACCOUNT_LAST4','ACCOUNT_LAST4','من حساب','LABELED_LINE',1,'PRIMARY','LAST4')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 28, true, MasroofDatabase.MIGRATION_27_28)
        db.query("SELECT COUNT(*) FROM message_pattern_families WHERE senderProfileId = 1").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*), MIN(familyId), MAX(familyId), SUM(exampleCount) FROM message_pattern_definitions").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
            assertEquals(it.getLong(1), it.getLong(2))
            assertEquals(55, it.getInt(3))
        }
        db.query("SELECT COUNT(*) FROM pattern_field_definitions WHERE patternId = 1").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        db.close()
    }
}
