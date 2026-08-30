package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomLoanRegistryRepository
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.LoanReference
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.model.OwnershipStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration8To9Test {
    @Test
    fun migrate8To9_dedupesLoanRowsAndAssignsStableIds() = runBlocking {
        val schema8 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/8.json")
        assertTrue(schema8.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-8-9.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema8, expectedVersion = 8)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        val duplicateId1 = RegistryEntityIdFactory.newLoanId()
        val duplicateId2 = RegistryEntityIdFactory.newLoanId()
        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO loan_registry
                (id, bankId, loanType, ownershipStatus, displayName, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('$duplicateId1', 'BANK_ALJAZIRA', 'PERSONAL', 'UNKNOWN', NULL, 'sms-1', 'sms-1')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO loan_registry
                (id, bankId, loanType, ownershipStatus, displayName, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('$duplicateId2', 'BANK_ALJAZIRA', 'PERSONAL', 'OWNED', 'تمويل شخصي', 'sms-2', 'sms-3')
                """.trimIndent(),
            )
            assertEquals(8, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(10, room.openHelper.writableDatabase.version)

            val loanRepo = RoomLoanRegistryRepository.from(room)
            val loans = loanRepo.listAll()
            assertEquals(1, loans.size)

            val loan = loans.single()
            val expectedId = RegistryEntityIdFactory.stableLoanId(
                Bank.BANK_ALJAZIRA.id,
                LoanType.PERSONAL.name,
            )
            assertEquals(expectedId, loan.id)
            assertEquals(OwnershipStatus.OWNED, loan.ownership)
            assertEquals("تمويل شخصي", loan.displayName)
            assertEquals("sms-1", loan.firstSeenRawSmsId)
            assertEquals("sms-3", loan.lastSeenRawSmsId)

            val resolved = loanRepo.get(LoanReference(Bank.BANK_ALJAZIRA, LoanType.PERSONAL))!!
            assertEquals(expectedId, resolved.id)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun applyExportedSchema(
        db: SupportSQLiteDatabase,
        schemaFile: java.io.File,
        expectedVersion: Int,
    ) {
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val database = root.getValue("database").jsonObject
        assertEquals(expectedVersion, database.getValue("version").jsonPrimitive.content.toInt())
        for (entityEl in database.getValue("entities").jsonArray) {
            val entity = entityEl.jsonObject
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            db.execSQL(
                entity.getValue("createSql").jsonPrimitive.content
                    .replace("\${TABLE_NAME}", tableName),
            )
            for (indexEl in entity["indices"]?.jsonArray.orEmpty()) {
                db.execSQL(
                    indexEl.jsonObject.getValue("createSql").jsonPrimitive.content
                        .replace("\${TABLE_NAME}", tableName),
                )
            }
        }
        for (setupEl in database["setupQueries"]?.jsonArray.orEmpty()) {
            db.execSQL(setupEl.jsonPrimitive.content)
        }
    }
}
