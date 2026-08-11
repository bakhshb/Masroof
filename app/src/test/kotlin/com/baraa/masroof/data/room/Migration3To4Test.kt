package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.repository.RoomReviewRepository
import com.baraa.masroof.data.repository.RoomUserCorrectionRepository
import com.baraa.masroof.data.room.migration.MIGRATION_1_2
import com.baraa.masroof.data.room.migration.MIGRATION_2_3
import com.baraa.masroof.data.room.migration.MIGRATION_3_4
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.ReviewKind
import com.baraa.masroof.domain.model.UserCorrection
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
class Migration3To4Test {

    @Test
    fun migrate3To4_fromExportedSchema_preservesP8AndCreatesReviewTables() = runBlocking {
        val schema3 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/3.json")
        assertTrue(schema3.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-3-4.db"
        context.deleteDatabase(dbName)

        val body = "preserved-p8"
        val receivedAt = Instant.parse("2026-08-01T12:00:00Z")
        val bodyHash = SmsBodyHasher.sha256Hex(body)
        val dedupeKey = "AlJazira|${receivedAt.toEpochMilli()}|$bodyHash"
        val txId = TransactionIdFactory.fromRawSmsIds(listOf("android-sms:1"))

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema3, expectedVersion = 3)
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
            db.execSQL(
                """
                INSERT INTO financial_transaction
                (id, type, amountDecimal, amountCurrency, occurredAtEpochMillis,
                 sourceContainerId, destinationContainerId, merchant, counterparty, categoryId)
                VALUES (?, 'EXPENSE', '51.99', 'SAR', ?, 'card:BANK_ALJAZIRA:7271', NULL, 'Keeta', NULL, NULL)
                """.trimIndent(),
                arrayOf(txId, receivedAt.toEpochMilli()),
            )
            db.execSQL(
                """
                INSERT INTO financial_transaction_raw_sms_link (rawSmsId, transactionId)
                VALUES ('android-sms:1', ?)
                """.trimIndent(),
                arrayOf(txId),
            )
            assertEquals(3, db.version)
            MIGRATION_3_4.migrate(db)
            db.version = 4
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("preserved-p8", room.rawSmsDao().getById("android-sms:1")!!.body)
            assertEquals("PURCHASE", room.parsedEventDao().getById("pe-1")!!.messageFamily)
            val accountRepo = RoomAccountRegistryRepository(room.accountRegistryDao())
            assertEquals(
                OwnershipStatus.OWNED,
                accountRepo.resolve(AccountReference(Bank.BANK_ALJAZIRA, "3001")),
            )
            val ftRepo = RoomFinancialTransactionRepository(
                room.financialTransactionDao(),
                room.parsedEventDao(),
            )
            assertEquals(txId, ftRepo.findByRawSmsId("android-sms:1")!!.id)

            val reviewRepo = RoomReviewRepository(room.reviewItemDao())
            reviewRepo.upsertRequired(
                rawSmsId = "android-sms:1",
                kind = ReviewKind.NEEDS_REVIEW,
                reasons = listOf("test_reason"),
                now = receivedAt,
            )
            assertEquals("android-sms:1", reviewRepo.findByRawSmsId("android-sms:1")!!.rawSmsId)

            val correctionRepo = RoomUserCorrectionRepository(room.userCorrectionDao())
            correctionRepo.save(
                UserCorrection(
                    id = "corr-1",
                    targetRawSmsId = "android-sms:1",
                    correctedType = MessageFamily.REFUND,
                    correctedAmount = Money.of(BigDecimal("10.00"), Currency.SAR),
                    correctedMerchant = null,
                    correctedCounterparty = null,
                    createdAt = receivedAt,
                ),
            )
            assertEquals("corr-1", correctionRepo.latestForRawSmsId("android-sms:1")!!.id)

            val schema4 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/4.json")
            assertTrue(schema4.isFile)
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate1To2To3To4_chainPreservesEvidence() = runBlocking {
        val schema1 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/1.json")
        assertTrue(schema1.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-1-2-3-4.db"
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
            MIGRATION_3_4.migrate(db)
            db.version = 4
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
            assertTrue(room.reviewItemDao().listAll().isEmpty())
            assertTrue(room.userCorrectionDao().listForRawSmsId("r1").isEmpty())
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
