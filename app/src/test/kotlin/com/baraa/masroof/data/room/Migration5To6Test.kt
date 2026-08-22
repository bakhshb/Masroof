package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomCardRegistryRepository
import com.baraa.masroof.data.room.migration.MIGRATION_5_6
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.OwnershipStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration5To6Test {
    @Test
    fun migrate5To6_addsRegistryMetadataColumnsAndPreservesRows() = runBlocking {
        val schema5 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/5.json")
        assertTrue(schema5.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-5-6.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema5, expectedVersion = 5)
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

        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '7271', 'OWNED', 'sms-1', 'sms-1')
                """.trimIndent(),
            )
            assertEquals(5, db.version)
            MIGRATION_5_6.migrate(db)
            db.version = 6
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val cardRepo = RoomCardRegistryRepository(room.cardRegistryDao())
            val loaded = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "7271"))!!
            assertEquals(OwnershipStatus.OWNED, loaded.ownership)
            assertNull(loaded.displayName)
            assertNull(loaded.cardNetwork)
            assertNull(loaded.cardType)
            assertNull(loaded.cardRole)

            cardRepo.updateDisplayName(CardReference(Bank.BANK_ALJAZIRA, "7271"), "Main card")
            cardRepo.updateCardNetwork(
                CardReference(Bank.BANK_ALJAZIRA, "7271"),
                com.baraa.masroof.domain.model.CardNetwork.VISA,
            )
            cardRepo.setPrimaryCard(CardReference(Bank.BANK_ALJAZIRA, "7271"))
            val updated = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "7271"))!!
            assertEquals("Main card", updated.displayName)
            assertEquals(com.baraa.masroof.domain.model.CardNetwork.VISA, updated.cardNetwork)
            assertEquals(CardRole.PRIMARY, updated.cardRole)

            val schema6 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/6.json")
            assertTrue(schema6.isFile)
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
    }
}
