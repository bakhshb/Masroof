package com.baraa.masroof.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Local Room database for the Masroof app.
 *
 * Schema version: **1** (single table `transactions`).
 *
 * This DB is **device-local only**: the application manifest disables cloud
 * backup and data extraction, no networking code touches this DB, and there is
 * no sync layer.
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MasroofDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        const val DATABASE_NAME: String = "masroof.db"

        fun build(context: Context): MasroofDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MasroofDatabase::class.java,
                DATABASE_NAME,
            )
                // For v1 there is no migration path yet; on a schema bump we
                // would add explicit Migration objects here.
                .fallbackToDestructiveMigration()
                .build()
    }
}
