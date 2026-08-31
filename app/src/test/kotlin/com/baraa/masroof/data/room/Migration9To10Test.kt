package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.room.mapper.ParsedEventMapper
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.parsing.model.CardSmsChannel
import com.baraa.masroof.parsing.model.ParsedEventDetails
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
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration9To10Test {
    @Test
    fun migrate9To10_preservesParsedEventsAndAddsDashboardFactColumns() = runBlocking {
        val schema9 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/9.json")
        assertTrue(schema9.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-9-10.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema9, expectedVersion = 9)
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

        val event = ParsedEvent(
            id = "evt-1",
            rawSmsId = "sms-1",
            bank = Bank.BANK_ALJAZIRA,
            messageFamily = MessageFamily.PURCHASE,
            direction = MoneyDirection.OUTGOING,
            amount = Money.of("51.99", Currency.SAR),
            purchaseChannel = null,
            sourceAccountRef = null,
            destinationAccountRef = null,
            cardRef = null,
            merchant = "SHOP",
            counterparty = null,
            occurredAt = null,
            bankNetworkType = null,
            confidence = Confidence(1.0),
            parseStatus = ParseStatus.SUCCESS,
        )

        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO raw_sms (id, sender, body, receivedAtEpochMillis, deviceMessageId, bodyHash, dedupeKey)
                VALUES ('sms-1', 'AlJazira', 'body', 1, '1', 'hash', 'key')
                """.trimIndent(),
            )
            val entity = ParsedEventMapper.toEntity(event, ParsedEventDetails())
            db.execSQL(
                """
                INSERT INTO parsed_event (
                    id, rawSmsId, bankId, messageFamily, direction,
                    amountDecimal, amountCurrency, purchaseChannel,
                    sourceAccountBankId, sourceAccountMaskedNumber,
                    destinationAccountBankId, destinationAccountMaskedNumber,
                    cardBankId, cardLast4, merchant, counterparty,
                    occurredAtEpochMillis, bankNetworkType,
                    confidenceScore, confidenceReasons, parseStatus,
                    transactionReference, availableBalanceDecimal, availableBalanceCurrency,
                    outstandingBalanceDecimal, outstandingBalanceCurrency,
                    biller, billerCode, occurredAtLocal
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    entity.id,
                    entity.rawSmsId,
                    entity.bankId,
                    entity.messageFamily,
                    entity.direction,
                    entity.amountDecimal,
                    entity.amountCurrency,
                    entity.purchaseChannel,
                    entity.sourceAccountBankId,
                    entity.sourceAccountMaskedNumber,
                    entity.destinationAccountBankId,
                    entity.destinationAccountMaskedNumber,
                    entity.cardBankId,
                    entity.cardLast4,
                    entity.merchant,
                    entity.counterparty,
                    entity.occurredAtEpochMillis,
                    entity.bankNetworkType,
                    entity.confidenceScore,
                    entity.confidenceReasons,
                    entity.parseStatus,
                    entity.transactionReference,
                    entity.availableBalanceDecimal,
                    entity.availableBalanceCurrency,
                    entity.outstandingBalanceDecimal,
                    entity.outstandingBalanceCurrency,
                    entity.biller,
                    entity.billerCode,
                    entity.occurredAtLocal,
                ),
            )
            assertEquals(9, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(MasroofDatabase.VERSION, room.openHelper.writableDatabase.version)
            val record = room.parsedEventDao().getById("evt-1")!!
            val mapped = ParsedEventMapper.toRecord(record)
            assertEquals(event, mapped.event)
            assertEquals(ParsedEventDetails(), mapped.details)
            room.parsedEventDao().replaceForRawSms(
                ParsedEventMapper.toEntity(
                    event,
                    ParsedEventDetails(
                        cardSmsChannel = CardSmsChannel.CREDIT,
                        paymentDueDate = LocalDate.parse("2026-09-07"),
                        exchangeRate = BigDecimal("3.756957"),
                        internationalFee = Money.of("1.99", Currency.SAR),
                        labeledForeignAmount = Money.of("23.00", Currency.USD),
                    ),
                ),
            )
            val refreshed = ParsedEventMapper.toRecord(room.parsedEventDao().getById("evt-1")!!)
            assertEquals(CardSmsChannel.CREDIT, refreshed.details.cardSmsChannel)
            assertEquals(LocalDate.parse("2026-09-07"), refreshed.details.paymentDueDate)
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
