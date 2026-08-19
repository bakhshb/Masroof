package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomFinancialTransactionRepository
import com.baraa.masroof.data.room.migration.MIGRATION_4_5
import com.baraa.masroof.domain.ids.TransactionIdFactory
import com.baraa.masroof.domain.model.ExchangeRateSource
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
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
import java.io.File
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration4To5Test {
    @Test
    fun migrate4To5_addsExchangeRateColumnsAndPreservesRows() = runBlocking {
        val schema4 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/4.json")
        assertTrue(schema4.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-4-5.db"
        context.deleteDatabase(dbName)
        val txId = TransactionIdFactory.fromRawSmsIds(listOf("android-sms:fx-1"))
        val occurredAt = Instant.parse("2026-08-17T15:23:00Z")

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema4, expectedVersion = 4)
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
                INSERT INTO financial_transaction
                (id, type, amountDecimal, amountCurrency, occurredAtEpochMillis,
                 sourceContainerId, destinationContainerId, merchant, counterparty, categoryId)
                VALUES (?, 'REFUND', '6.51', 'USD', ?, NULL, NULL, 'CURSOR', NULL, NULL)
                """.trimIndent(),
                arrayOf(txId, occurredAt.toEpochMilli()),
            )
            assertEquals(4, db.version)
            MIGRATION_4_5.migrate(db)
            db.version = 5
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val ftRepo = RoomFinancialTransactionRepository(
                room.financialTransactionDao(),
                room.parsedEventDao(),
            )
            val loaded = ftRepo.getById(txId)!!
            assertEquals(Money.of("6.51", Currency.USD), loaded.amount)
            assertNull(loaded.appliedExchangeRate)
            assertNull(loaded.exchangeRateSource)

            val updated = loaded.copy(
                appliedExchangeRate = BigDecimal("3.756957"),
                exchangeRateSource = ExchangeRateSource.MARKET,
            )
            assertTrue(ftRepo.update(updated))
            val reloaded = ftRepo.getById(txId)!!
            assertEquals(BigDecimal("3.756957"), reloaded.appliedExchangeRate)
            assertEquals(ExchangeRateSource.MARKET, reloaded.exchangeRateSource)

            val schema5 = File("schemas/com.baraa.masroof.data.room.MasroofDatabase/5.json")
            assertTrue(schema5.isFile)
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
