package com.baraa.masroof.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baraa.masroof.data.room.dao.AccountRegistryDao
import com.baraa.masroof.data.room.dao.CardRegistryDao
import com.baraa.masroof.data.room.dao.ParsedEventDao
import com.baraa.masroof.data.room.dao.RawSmsDao
import com.baraa.masroof.data.room.entity.AccountRegistryEntity
import com.baraa.masroof.data.room.entity.CardRegistryEntity
import com.baraa.masroof.data.room.entity.ParsedEventEntity
import com.baraa.masroof.data.room.entity.RawSmsEntity
import com.baraa.masroof.data.room.migration.MIGRATION_1_2

/**
 * Clean rewrite persistence schema — version 2 (P7 ownership registries).
 *
 * Migration 1→2 adds account_registry and card_registry only.
 * Does not use destructive migration.
 */
@Database(
    entities = [
        RawSmsEntity::class,
        ParsedEventEntity::class,
        AccountRegistryEntity::class,
        CardRegistryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MasroofDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao

    abstract fun parsedEventDao(): ParsedEventDao

    abstract fun accountRegistryDao(): AccountRegistryDao

    abstract fun cardRegistryDao(): CardRegistryDao

    companion object {
        const val NAME: String = "masroof.db"

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
