package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.data.repository.RoomCommitmentRepository
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
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
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Migration9To10Test {
    @Test
    fun migrate9To10_createsCommitmentTable() = runBlocking {
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

        openHelper.writableDatabase.use { db ->
            assertEquals(9, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(10, room.openHelper.writableDatabase.version)

            val repo = RoomCommitmentRepository.from(room)
            val now = Instant.parse("2026-08-01T00:00:00Z")
            val commitment = Commitment(
                id = RegistryEntityIdFactory.newCommitmentId(),
                name = "Netflix",
                amount = Money.of("71.00", Currency.SAR),
                transactionDate = LocalDate.parse("2026-08-01"),
                recurrence = CommitmentRecurrence.MONTHLY,
                dueDate = LocalDate.parse("2026-08-05"),
                active = true,
                sourceTransactionId = "tx-1",
                createdAt = now,
                updatedAt = now,
            )
            repo.create(commitment)

            val loaded = repo.get(commitment.id)!!
            assertEquals("Netflix", loaded.name)
            assertEquals(CommitmentRecurrence.MONTHLY, loaded.recurrence)
            assertEquals("tx-1", loaded.sourceTransactionId)
            assertEquals(1, repo.listActive().size)
        } finally {
            room.close()
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
