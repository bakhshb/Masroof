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
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.AccountType
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.CardRole
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.OwnershipStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration6To7Test {
    @Test
    fun migrate6To7_preservesDataAndBuildsHierarchy() = runBlocking {
        val schema6 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/6.json")
        assertTrue(schema6.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-6-7.db"
        context.deleteDatabase(dbName)

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema6, expectedVersion = 6)
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
                INSERT INTO account_registry
                (bankId, maskedNumber, ownershipStatus, displayName, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '****3001', 'OWNED', 'Current', 'sms-a', 'sms-a')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, cardType, linkedAccountBankId, linkedAccountMaskedNumber,
                 firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '5555', 'OWNED', 'DEBIT', 'BANK_ALJAZIRA', '****3001', 'sms-d1', 'sms-d1')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, cardType, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '8888', 'OWNED', 'DEBIT', 'sms-d2', 'sms-d2')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, cardType, cardRole, firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '1111', 'OWNED', 'CREDIT', 'PRIMARY', 'sms-c1', 'sms-c1')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO card_registry
                (bankId, last4, ownershipStatus, cardType, cardRole, parentCardLast4,
                 firstSeenRawSmsId, lastSeenRawSmsId)
                VALUES ('BANK_ALJAZIRA', '2222', 'OWNED', 'CREDIT', 'SUPPLEMENTARY', '1111', 'sms-c2', 'sms-c2')
                """.trimIndent(),
            )
            assertEquals(6, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(9, room.openHelper.writableDatabase.version)

            val accountRepo = RoomAccountRegistryRepository.from(room)
            val cardRepo = RoomCardRegistryRepository.from(room)

            val account = accountRepo.get(AccountReference(Bank.BANK_ALJAZIRA, "****3001"))!!
            assertEquals(AccountType.CURRENT, account.accountType)
            assertEquals(
                FinancialContainerIdFactory.accountId(Bank.BANK_ALJAZIRA, "****3001"),
                "account:BANK_ALJAZIRA:****3001",
            )

            val linkedDebit = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "5555"))!!
            assertEquals(CardType.DEBIT, linkedDebit.cardType)
            assertEquals("****3001", linkedDebit.linkedAccountMaskedNumber)

            val primary = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "1111"))!!
            val facilityId = CreditFacilityIdFactory.facilityId(Bank.BANK_ALJAZIRA, "1111")
            assertEquals(facilityId, primary.creditFacilityId)

            val supplement = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "2222"))!!
            assertEquals(facilityId, supplement.creditFacilityId)
            assertEquals(CardRole.SUPPLEMENTARY, supplement.cardRole)

            val unlinkedDebit = cardRepo.get(CardReference(Bank.BANK_ALJAZIRA, "8888"))!!
            assertEquals(CardType.DEBIT, unlinkedDebit.cardType)
            assertEquals(null, unlinkedDebit.creditFacilityId)

            val facility = room.creditFacilityDao().get(facilityId)
            assertNotNull(facility)
            assertEquals("1111", facility!!.primaryLast4)

            val banks = room.bankRegistryDao().listAll()
            assertTrue(banks.any { it.bankId == Bank.BANK_ALJAZIRA.id })
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
