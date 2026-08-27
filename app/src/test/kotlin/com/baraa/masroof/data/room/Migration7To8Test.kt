package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.domain.ids.CreditFacilityIdFactory
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration7To8Test {
    @Test
    fun migrate7To8_assignsOpaqueEntityIdsAndRemapsFacilityIds() = runBlocking {
        val schema7 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/7.json")
        assertTrue(schema7.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-7-8.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema7, expectedVersion = 7)
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

        val legacyFacilityId = "facility:BANK_ALJAZIRA:1111"
        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO account_registry
                (bankId, maskedNumber, ownershipStatus, displayName, accountType,
                 firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '****3001', 'OWNED', 'Current', 'CURRENT', 'sms-a', 'sms-a')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO credit_facility (id, bankId, primaryLast4)
                VALUES ('$legacyFacilityId', 'BANK_ALJAZIRA', '1111')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, cardType, cardRole, creditFacilityId,
                 firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '1111', 'OWNED', 'CREDIT', 'PRIMARY', '$legacyFacilityId', 'sms-c', 'sms-c')
                """.trimIndent(),
            )
            assertEquals(7, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(8, room.openHelper.writableDatabase.version)

            val accountRepo = RoomAccountRegistryRepository.from(room)
            val cardRepo = RoomCardRegistryRepository.from(room)

            val account = accountRepo.get(AccountReference(Bank.BANK_ALJAZIRA, "****3001"))!!
            val expectedAccountId = RegistryEntityIdFactory.stableAccountId(
                Bank.BANK_ALJAZIRA.id,
                "****3001",
            )
            assertEquals(expectedAccountId, account.id)
            assertFalse(account.id.contains("3001"))

            val card = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "1111"))!!
            val expectedCardId = RegistryEntityIdFactory.stableCardId(Bank.BANK_ALJAZIRA.id, "1111")
            assertEquals(expectedCardId, card.id)
            assertFalse(card.id.contains("1111"))

            val expectedFacilityId = CreditFacilityIdFactory.facilityId(Bank.BANK_ALJAZIRA, "1111")
            assertEquals(expectedFacilityId, card.creditFacilityId)
            assertFalse(expectedFacilityId.startsWith("facility:"))

            val facility = room.creditFacilityDao().get(expectedFacilityId)
            assertTrue(facility != null)
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
