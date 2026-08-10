package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomAccountRegistryRepository
import com.baraa.masroof.data.repository.RoomRawSmsRepository
import com.baraa.masroof.data.room.migration.MIGRATION_1_2
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.sms.hash.SmsBodyHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Real 1→2 migration against an on-disk v1 SQLite database (not a fresh v2 DB).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration1To2Test {

    private val testDbName = "migration-1-2-test.db"

    @Test
    fun migrate1To2_preservesEvidence_andCreatesRegistries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(testDbName)

        val body = "exact preserved body"
        val receivedAt = Instant.parse("2026-08-01T12:00:00Z")
        val bodyHash = SmsBodyHasher.sha256Hex(body)
        val dedupeKey = "AlJazira|${receivedAt.toEpochMilli()}|$bodyHash"

        // Build a genuine schema-v1 database, insert evidence, then migrate.
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV1Schema(db)
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
        }
        openHelper.close()

        val roomDb = Room.databaseBuilder(context, MasroofDatabase::class.java, testDbName)
            .addMigrations(MIGRATION_1_2)
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

            val accountRepo = RoomAccountRegistryRepository(roomDb.accountRegistryDao())
            val ref = AccountReference(Bank.BANK_ALJAZIRA, "3001")
            accountRepo.observe(ref, "android-sms:1")
            assertEquals(OwnershipStatus.UNKNOWN, accountRepo.resolve(ref))

            val preserved = RoomRawSmsRepository(roomDb.rawSmsDao()).getById("android-sms:1")
            assertEquals(
                RawSms(
                    id = "android-sms:1",
                    sender = "AlJazira",
                    body = body,
                    receivedAt = receivedAt,
                    deviceMessageId = "1",
                    bodyHash = bodyHash,
                ),
                preserved,
            )
        } finally {
            roomDb.close()
            context.deleteDatabase(testDbName)
        }
    }

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `raw_sms` (
              `id` TEXT NOT NULL,
              `sender` TEXT NOT NULL,
              `body` TEXT NOT NULL,
              `receivedAtEpochMillis` INTEGER NOT NULL,
              `deviceMessageId` TEXT,
              `bodyHash` TEXT NOT NULL,
              `dedupeKey` TEXT NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_sms_dedupeKey` ON `raw_sms` (`dedupeKey`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_sms_deviceMessageId` ON `raw_sms` (`deviceMessageId`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `parsed_event` (
              `id` TEXT NOT NULL,
              `rawSmsId` TEXT NOT NULL,
              `bankId` TEXT NOT NULL,
              `messageFamily` TEXT NOT NULL,
              `direction` TEXT,
              `amountDecimal` TEXT,
              `amountCurrency` TEXT,
              `purchaseChannel` TEXT,
              `sourceAccountBankId` TEXT,
              `sourceAccountMaskedNumber` TEXT,
              `destinationAccountBankId` TEXT,
              `destinationAccountMaskedNumber` TEXT,
              `cardBankId` TEXT,
              `cardLast4` TEXT,
              `merchant` TEXT,
              `counterparty` TEXT,
              `occurredAtEpochMillis` INTEGER,
              `bankNetworkType` TEXT,
              `confidenceScore` REAL NOT NULL,
              `confidenceReasons` TEXT NOT NULL,
              `parseStatus` TEXT NOT NULL,
              `transactionReference` TEXT,
              `availableBalanceDecimal` TEXT,
              `availableBalanceCurrency` TEXT,
              `outstandingBalanceDecimal` TEXT,
              `outstandingBalanceCurrency` TEXT,
              `biller` TEXT,
              `billerCode` TEXT,
              `occurredAtLocal` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`rawSmsId`) REFERENCES `raw_sms`(`id`)
                ON UPDATE CASCADE ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_parsed_event_rawSmsId` ON `parsed_event` (`rawSmsId`)",
        )
    }
}
