package com.baraa.masroof.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.baraa.masroof.data.room.dao.AccountRegistryDao
import com.baraa.masroof.data.room.dao.CardRegistryDao
import com.baraa.masroof.data.room.dao.FinancialTransactionDao
import com.baraa.masroof.data.room.dao.ParsedEventDao
import com.baraa.masroof.data.room.dao.RawSmsDao
import com.baraa.masroof.data.room.dao.ReviewItemDao
import com.baraa.masroof.data.room.dao.UserCorrectionDao
import com.baraa.masroof.data.room.entity.AccountRegistryEntity
import com.baraa.masroof.data.room.entity.CardRegistryEntity
import com.baraa.masroof.data.room.entity.FinancialTransactionEntity
import com.baraa.masroof.data.room.entity.FinancialTransactionRawSmsLinkEntity
import com.baraa.masroof.data.room.entity.ParsedEventEntity
import com.baraa.masroof.data.room.entity.RawSmsEntity
import com.baraa.masroof.data.room.entity.ReviewItemEntity
import com.baraa.masroof.data.room.entity.UserCorrectionEntity
import com.baraa.masroof.data.room.migration.MIGRATION_1_2
import com.baraa.masroof.data.room.migration.MIGRATION_2_3
import com.baraa.masroof.data.room.migration.MIGRATION_3_4
import com.baraa.masroof.data.room.migration.MIGRATION_4_5
import com.baraa.masroof.data.room.migration.MIGRATION_5_6

/**
 * Clean rewrite persistence schema — version 4 (P9 review + user corrections).
 *
 * Migrations: 1→2 ownership registries; 2→3 financial transactions; 3→4 review workflow;
 * 4→5 exchange rates; 5→6 registry display names and card relationships.
 * Does not use destructive migration.
 */
@Database(
    entities = [
        RawSmsEntity::class,
        ParsedEventEntity::class,
        AccountRegistryEntity::class,
        CardRegistryEntity::class,
        FinancialTransactionEntity::class,
        FinancialTransactionRawSmsLinkEntity::class,
        ReviewItemEntity::class,
        UserCorrectionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MasroofDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao

    abstract fun parsedEventDao(): ParsedEventDao

    abstract fun accountRegistryDao(): AccountRegistryDao

    abstract fun cardRegistryDao(): CardRegistryDao

    abstract fun financialTransactionDao(): FinancialTransactionDao

    abstract fun reviewItemDao(): ReviewItemDao

    abstract fun userCorrectionDao(): UserCorrectionDao

    companion object {
        const val NAME: String = "masroof.db"
        const val VERSION: Int = 6

        /** Must match app/schemas/.../6.json identityHash — updated after schema export. */
        const val IDENTITY_HASH: String = "bae309478a1209a31e06cfcbde30b56f"

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
