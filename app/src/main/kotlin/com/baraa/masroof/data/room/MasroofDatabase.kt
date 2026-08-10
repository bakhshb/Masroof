package com.baraa.masroof.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baraa.masroof.data.room.dao.ParsedEventDao
import com.baraa.masroof.data.room.dao.RawSmsDao
import com.baraa.masroof.data.room.entity.ParsedEventEntity
import com.baraa.masroof.data.room.entity.RawSmsEntity

/**
 * Clean rewrite persistence schema — version 1.
 *
 * No migrations from the legacy Masroof database.
 */
@Database(
    entities = [
        RawSmsEntity::class,
        ParsedEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MasroofDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao

    abstract fun parsedEventDao(): ParsedEventDao

    companion object {
        const val NAME: String = "masroof.db"
    }
}
