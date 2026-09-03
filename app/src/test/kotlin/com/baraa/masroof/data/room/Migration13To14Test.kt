package com.baraa.masroof.data.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.data.repository.RoomCommitmentRepository
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
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
class Migration13To14Test {
    @Test
    fun migrate13To14_backfillsPauseIntervalsForLegacyInactiveRows() = runBlocking {
        val schema13 = java.io.File("schemas/com.baraa.masroof.data.room.MasroofDatabase/13.json")
        assertTrue(schema13.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-13-14.db"
        context.deleteDatabase(dbName)

        val pausedAt = Instant.parse("2026-08-15T00:00:00Z")
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(13) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            applyExportedSchema(db, schema13, expectedVersion = 13)
                            db.execSQL(
                                """
                                INSERT INTO commitment (
                                    id, name, amountDecimal, amountCurrency, transactionDateIso,
                                    recurrence, dueDateIso, active, pauseIntervalsJson,
                                    sourceTransactionId, createdAtEpochMillis, updatedAtEpochMillis
                                ) VALUES (
                                    'cmt_legacy', 'STC', '173.00', 'SAR', '2026-07-01',
                                    'MONTHLY', NULL, 0, '[]',
                                    'tx-stc', ${pausedAt.toEpochMilli()}, ${pausedAt.toEpochMilli()}
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
            assertEquals(13, db.version)
        }
        openHelper.close()

        val room = Room.databaseBuilder(context, MasroofDatabase::class.java, dbName)
            .addMigrations(*MasroofDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(MasroofDatabase.VERSION, room.openHelper.writableDatabase.version)

            val repo = RoomCommitmentRepository.from(room)
            val loaded = repo.get("cmt_legacy")!!
            assertEquals(1, loaded.pauseIntervals.size)
            assertEquals(pausedAt, loaded.pauseIntervals.single().pausedAt)
            assertEquals(null, loaded.pauseIntervals.single().resumedAt)

            val active = Commitment(
                id = RegistryEntityIdFactory.newCommitmentId(),
                name = "Netflix",
                amount = Money.of("71.00", Currency.SAR),
                transactionDate = LocalDate.parse("2026-08-01"),
                recurrence = CommitmentRecurrence.MONTHLY,
                dueDate = null,
                active = true,
                sourceTransactionId = "tx-netflix",
                createdAt = Instant.parse("2026-08-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
            )
            repo.create(active)
            assertEquals(1, repo.listActive().size)
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
