package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.room.mapper.ParsedEventMapper
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.parsing.model.ParsedEventDetails
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
class Migration10To11Test {
    @Test
    fun migrate10To11_preservesParsedEventsAndAddsBankNeutralFactColumns() = runBlocking {
        val schema10 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/10.json")
        assertTrue(schema10.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-10-11.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema10, expectedVersion = 10)
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
            assertEquals(10, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(MasroofDatabase.VERSION, room.openHelper.writableDatabase.version)
            room.rawSmsDao().insertIfAbsent(
                com.baraa.masroof.data.room.entity.RawSmsEntity(
                    id = "sms-1",
                    sender = "AlJazira",
                    body = "body",
                    receivedAtEpochMillis = 1L,
                    deviceMessageId = "1",
                    bodyHash = "hash",
                    dedupeKey = "key",
                ),
            )
            room.parsedEventDao().replaceForRawSms(
                ParsedEventMapper.toEntity(
                    com.baraa.masroof.domain.model.ParsedEvent(
                        id = "evt-1",
                        rawSmsId = "sms-1",
                        bank = com.baraa.masroof.domain.model.Bank.BANK_ALJAZIRA,
                        messageFamily = com.baraa.masroof.domain.model.MessageFamily.FINANCING_INSTALLMENT,
                        direction = com.baraa.masroof.domain.model.MoneyDirection.OUTGOING,
                        amount = null,
                        purchaseChannel = null,
                        sourceAccountRef = null,
                        destinationAccountRef = null,
                        cardRef = null,
                        merchant = null,
                        counterparty = "تمويل شخصي",
                        occurredAt = null,
                        bankNetworkType = null,
                        confidence = com.baraa.masroof.domain.model.Confidence(1.0),
                        parseStatus = com.baraa.masroof.domain.model.ParseStatus.SUCCESS,
                    ),
                    ParsedEventDetails(
                        loanType = LoanType.PERSONAL,
                        debitSourceAccountLast4 = "3001",
                        salaryIncomeWording = true,
                    ),
                ),
            )
            val refreshed = ParsedEventMapper.toRecord(room.parsedEventDao().getById("evt-1")!!)
            assertEquals(LoanType.PERSONAL, refreshed.details.loanType)
            assertEquals("3001", refreshed.details.debitSourceAccountLast4)
            assertEquals(true, refreshed.details.salaryIncomeWording)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate10To11_leavesNewColumnsNullForLegacyRows() = runBlocking {
        val schema10 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/10.json")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-10-11-legacy.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema10, expectedVersion = 10)
                            db.execSQL(
                                """
                                INSERT INTO raw_sms (id, sender, body, receivedAtEpochMillis, deviceMessageId, bodyHash, dedupeKey)
                                VALUES ('sms-legacy', 'AlJazira', 'body', 1, '1', 'hash', 'key')
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO parsed_event (
                                    id, rawSmsId, bankId, messageFamily, confidenceScore, confidenceReasons, parseStatus
                                ) VALUES (
                                    'evt-legacy', 'sms-legacy', 'BANK_ALJAZIRA', 'PURCHASE', 1.0, '', 'SUCCESS'
                                )
                                """.trimIndent(),
                            )
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
            assertEquals(1, db.query("SELECT COUNT(*) FROM parsed_event").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            })
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            db.query(
                """
                SELECT loanType, debitSourceAccountLast4, salaryIncomeWording
                FROM parsed_event
                WHERE id = 'evt-legacy'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
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
