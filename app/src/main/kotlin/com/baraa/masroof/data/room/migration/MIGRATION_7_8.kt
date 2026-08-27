package com.baraa.masroof.data.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory

/**
 * Opaque registry entity ids; remap legacy facility:* credit facility ids.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE account_registry ADD COLUMN id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE card_registry ADD COLUMN id TEXT NOT NULL DEFAULT ''")

        db.query("SELECT bankId, maskedNumber FROM account_registry").use { cursor ->
            val bankIdx = cursor.getColumnIndexOrThrow("bankId")
            val maskIdx = cursor.getColumnIndexOrThrow("maskedNumber")
            while (cursor.moveToNext()) {
                val bankId = cursor.getString(bankIdx)
                val masked = cursor.getString(maskIdx)
                val id = RegistryEntityIdFactory.stableAccountId(bankId, masked)
                db.execSQL(
                    "UPDATE account_registry SET id = ? WHERE bankId = ? AND maskedNumber = ?",
                    arrayOf(id, bankId, masked),
                )
            }
        }

        db.query("SELECT bankId, last4 FROM card_registry").use { cursor ->
            val bankIdx = cursor.getColumnIndexOrThrow("bankId")
            val last4Idx = cursor.getColumnIndexOrThrow("last4")
            while (cursor.moveToNext()) {
                val bankId = cursor.getString(bankIdx)
                val last4 = cursor.getString(last4Idx)
                val id = RegistryEntityIdFactory.stableCardId(bankId, last4)
                db.execSQL(
                    "UPDATE card_registry SET id = ? WHERE bankId = ? AND last4 = ?",
                    arrayOf(id, bankId, last4),
                )
            }
        }

        val facilityIdRemap = mutableMapOf<String, String>()
        db.query("SELECT id, bankId, primaryLast4 FROM credit_facility").use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val bankIdx = cursor.getColumnIndexOrThrow("bankId")
            val primaryIdx = cursor.getColumnIndexOrThrow("primaryLast4")
            while (cursor.moveToNext()) {
                val oldId = cursor.getString(idIdx)
                val bankId = cursor.getString(bankIdx)
                val primaryLast4 = cursor.getString(primaryIdx)
                val newId = RegistryEntityIdFactory.stableCreditFacilityId(bankId, primaryLast4)
                facilityIdRemap[oldId] = newId
            }
        }

        for ((oldId, newId) in facilityIdRemap) {
            if (oldId == newId) continue
            db.execSQL(
                "UPDATE credit_facility SET id = ? WHERE id = ?",
                arrayOf(newId, oldId),
            )
            db.execSQL(
                "UPDATE card_registry SET creditFacilityId = ? WHERE creditFacilityId = ?",
                arrayOf(newId, oldId),
            )
        }

        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_account_registry_id
            ON account_registry (id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_card_registry_id
            ON card_registry (id)
            """.trimIndent(),
        )
    }
}
