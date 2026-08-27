package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.room.migration.MIGRATION_1_2
import com.baraa.masroof.data.room.migration.MIGRATION_2_3
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
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
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration2To3Test {

    @Test
    fun migrate2To3_fromExportedSchema_preservesP7AndCreatesTransactions() = runBlocking {
        val schema2 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/2.json")
        assertTrue(schema2.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-2-3.db"
        context.deleteDatabase(dbName)

        val body = "preserved"
        val receivedAt = Instant.parse("2026-08-01T12:00:00Z")
        val bodyHash = SmsBodyHasher.sha256Hex(body)
        val dedupeKey = "AlJazira|${receivedAt.toEpochMilli()}|$bodyHash"

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema2, expectedVersion = 2)
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
                arrayOf("android-sms:1", "AlJazira", body, receivedAt.toEpochMilli(), "1", bodyHash, dedupeKey),
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
                  '51.99', 'SAR', 'ONLINE', NULL, NULL, NULL, NULL,
                  'BANK_ALJAZIRA', '7271', 'Keeta', NULL, NULL,
                  NULL, 1.0, '', 'SUCCESS',
                  NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO account_registry
                (bankId, maskedNumber, ownershipStatus, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '3001', 'OWNED', 'android-sms:1', 'android-sms:1')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '7271', 'UNKNOWN', 'android-sms:1', 'android-sms:1')
                """.trimIndent(),
            )
            assertEquals(2, db.version)
            MIGRATION_2_3.migrate(db)
            db.version = 3
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("preserved", room.rawSmsDao().getById("android-sms:1")!!.body)
            assertEquals("PURCHASE", room.parsedEventDao().getById("pe-1")!!.messageFamily)
            val accountRepo = RoomAccountRegistryRepository.from(room)
            assertEquals(
                OwnershipStatus.OWNED,
                accountRepo.resolve(AccountReference(Bank.BANK_ALJAZIRA, "3001")),
            )
            assertEquals("android-sms:1", accountRepo.get(AccountReference(Bank.BANK_ALJAZIRA, "3001"))!!.firstSeenRawSmsId)

            val ftRepo = RoomFinancialTransactionRepository(
                room.financialTransactionDao(),
                room.parsedEventDao(),
            )
            val tx = FinancialTransaction(
                id = TransactionIdFactory.fromRawSmsIds(listOf("android-sms:1")),
                type = FinancialTransactionType.EXPENSE,
                amount = Money.of(BigDecimal("51.99"), Currency.SAR),
                occurredAt = receivedAt,
                sourceContainerId = "card:BANK_ALJAZIRA:7271",
                destinationContainerId = null,
                merchant = "Keeta",
                counterparty = null,
                categoryId = null,
                linkedParsedEventIds = listOf("pe-1"),
            )
            ftRepo.save(tx, listOf("android-sms:1"))
            assertEquals(tx.id, ftRepo.findByRawSmsId("android-sms:1")!!.id)

            val tables = room.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'financial_%'",
            )
            val names = mutableSetOf<String>()
            tables.use { while (it.moveToNext()) names += it.getString(0) }
            assertTrue(names.contains("financial_transaction"))
            assertTrue(names.contains("financial_transaction_raw_sms_link"))
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate1To2To3_chainPreservesEvidence() = runBlocking {
        val schema1 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/1.json")
        assertTrue(schema1.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-1-2-3.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema1, expectedVersion = 1)
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
                VALUES ('r1', 'AlJazira', 'x', 1, 'd1', 'h', 'k')
                """.trimIndent(),
            )
            assertEquals(1, db.version)
            MIGRATION_1_2.migrate(db)
            db.version = 2
            MIGRATION_2_3.migrate(db)
            db.version = 3
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("x", room.rawSmsDao().getById("r1")!!.body)
            assertTrue(room.accountRegistryDao().listAll().isEmpty())
            assertEquals(0, room.financialTransactionDao().count())
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun applyExportedSchema(
        db: SupportSQLiteDatabase,
        schemaFile: File,
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
