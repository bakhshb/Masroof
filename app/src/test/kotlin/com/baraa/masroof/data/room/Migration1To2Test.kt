package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.migration.MIGRATION_1_2
import com.baraa.masroof.data.room.migration.MIGRATION_2_3
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.sms.hash.SmsBodyHasher
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
import java.io.File
import java.time.Instant

/**
 * Migration proof against the **committed** exported Room schema
 * `schemas/.../MasroofDatabase/1.json` (not a hand-maintained SQL duplicate).
 *
 * Flow: exported v1 → [MIGRATION_1_2] (assert registries) → [MIGRATION_2_3] →
 * open current Room and assert evidence survives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration1To2Test {

    private val testDbName = "migration-1-2-exported.db"

    @Test
    fun migrate1To2_fromExportedSchema_preservesEvidence() = runBlocking {
        val schemaFile = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/1.json")
        assertTrue("committed exported v1 schema must exist", schemaFile.isFile)

        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(testDbName)

        val body = "exact preserved body"
        val receivedAt = Instant.parse("2026-08-01T12:00:00Z")
        val bodyHash = SmsBodyHasher.sha256Hex(body)
        val dedupeKey = "AlJazira|${receivedAt.toEpochMilli()}|$bodyHash"

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schemaFile)
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
                INSERT INTO raw_sms
                (id, sender, body, receivedAtEpochMillis, deviceMessageId, bodyHash, dedupeKey)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    "android-sms:1",
                    "AlJazira",
                    body,
                    receivedAt.toEpochMilli(),
                    "1",
                    bodyHash,
                    dedupeKey,
                ),
            )
            db.execSQL(
                """
                INSERT INTO parsed_event
                (id, rawSmsId, bankId, messageFamily, direction, amountDecimal, amountCurrency,
                 purchaseChannel, sourceAccountBankId, sourceAccountMaskedNumber,
                 destinationAccountBankId, destinationAccountMaskedNumber,
                 cardBankId, cardLast4, merchant, counterparty, occurredAtEpochMillis,
                 bankNetworkType, confidenceScore, confidenceReasons, parseStatus,
                 transactionReference, availableBalanceDecimal, availableBalanceCurrency,
                 outstandingBalanceDecimal, outstandingBalanceCurrency,
                 biller, billerCode, occurredAtLocal)
                VALUES (
                  'pe-1', 'android-sms:1', 'BANK_ALJAZIRA', 'PURCHASE', 'OUTGOING',
                  '51.99', 'SAR', 'ONLINE', 'BANK_ALJAZIRA', '3001',
                  NULL, NULL, 'BANK_ALJAZIRA', '7271', 'Keeta', NULL, NULL,
                  NULL, 1.0, '', 'SUCCESS',
                  NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
            assertEquals(1, db.version)
            MIGRATION_1_2.migrate(db)
            db.version = 2

            val registryTables = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('account_registry','card_registry')",
            )
            val foundAtV2 = mutableSetOf<String>()
            registryTables.use {
                while (it.moveToNext()) foundAtV2 += it.getString(0)
            }
            assertTrue(foundAtV2.contains("account_registry"))
            assertTrue(foundAtV2.contains("card_registry"))

            // Continue to current Room version so schema validation succeeds.
            MIGRATION_2_3.migrate(db)
            db.version = 3
        }
        openHelper.close()

        val roomDb = Room.databaseBuilder(context, MasroofDatabase::class.java, testDbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val raw = roomDb.rawSmsDao().getById("android-sms:1")
            assertEquals(body, raw!!.body)
            assertEquals(receivedAt.toEpochMilli(), raw.receivedAtEpochMillis)

            val parsed = roomDb.parsedEventDao().getById("pe-1")
            assertEquals("android-sms:1", parsed!!.rawSmsId)
            assertEquals("PURCHASE", parsed.messageFamily)
            assertEquals("7271", parsed.cardLast4)

            val tables = roomDb.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('account_registry','card_registry')",
            )
            val found = mutableSetOf<String>()
            tables.use {
                while (it.moveToNext()) found += it.getString(0)
            }
            assertTrue(found.contains("account_registry"))
            assertTrue(found.contains("card_registry"))

            val schema2 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/2.json")
            assertTrue(schema2.isFile)
            assertTrue(schema2.readText().contains("account_registry"))
            assertTrue(!schema2.readText().contains("evidenceCount"))

            val accountRepo = RoomAccountRegistryRepository.from(roomDb)
            val ref = AccountReference(Bank.BANK_ALJAZIRA, "3001")
            accountRepo.observe(ref, "android-sms:1")
            assertEquals(OwnershipStatus.UNKNOWN, accountRepo.resolve(ref))

            assertEquals(
                RawSms(
                    id = "android-sms:1",
                    sender = "AlJazira",
                    body = body,
                    receivedAt = receivedAt,
                    deviceMessageId = "1",
                    bodyHash = bodyHash,
                ),
                RoomRawSmsRepository(roomDb.rawSmsDao()).getById("android-sms:1"),
            )
        } finally {
            roomDb.close()
            context.deleteDatabase(testDbName)
        }
    }

    private fun applyExportedSchema(db: SupportSQLiteDatabase, schemaFile: File) {
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val database = root.getValue("database").jsonObject
        assertEquals(1, database.getValue("version").jsonPrimitive.content.toInt())

        val entities = database.getValue("entities").jsonArray
        for (entityEl in entities) {
            val entity = entityEl.jsonObject
            val tableName = entity.getValue("tableName").jsonPrimitive.content
            val createSql = entity.getValue("createSql").jsonPrimitive.content
                .replace("\${TABLE_NAME}", tableName)
            db.execSQL(createSql)

            val indices = entity["indices"]?.jsonArray.orEmpty()
            for (indexEl in indices) {
                val index = indexEl.jsonObject
                val indexSql = index.getValue("createSql").jsonPrimitive.content
                    .replace("\${TABLE_NAME}", tableName)
                db.execSQL(indexSql)
            }
        }
    }
}
