package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * First-class SMS sender (Android address). Many accounts may share one profile.
 * Does not identify the exact financial instrument — typed identifiers do.
 */
@Entity(
    tableName = "sender_profiles",
    indices = [
        Index(value = ["normalizedSenderKey"], unique = true),
        Index(value = ["active"]),
    ],
)
data class SenderProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "displaySender")
    val displaySender: String,
    @ColumnInfo(name = "normalizedSenderKey")
    val normalizedSenderKey: String,
    @ColumnInfo(name = "institutionId")
    val institutionId: String? = null,
    @ColumnInfo(name = "displayInstitutionName")
    val displayInstitutionName: String? = null,
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/**
 * Many-to-many: FinancialAccount ↔ SenderProfile.
 * Narrows matching candidates; never alone confirms an account.
 */
@Entity(
    tableName = "account_sender_profiles",
    primaryKeys = ["accountId", "senderProfileId"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["senderProfileId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SenderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AccountSenderProfileCrossRef(
    val accountId: Long,
    val senderProfileId: Long,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
)

@Dao
interface SenderProfileDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SenderProfileEntity): Long

    @Update
    suspend fun update(row: SenderProfileEntity)

    @Query("SELECT * FROM sender_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SenderProfileEntity?

    @Query("SELECT * FROM sender_profiles WHERE normalizedSenderKey = :key LIMIT 1")
    suspend fun findByKey(key: String): SenderProfileEntity?

    @Query("SELECT * FROM sender_profiles WHERE active = 1 ORDER BY displaySender ASC")
    suspend fun getActive(): List<SenderProfileEntity>

    @Query("SELECT * FROM sender_profiles WHERE active = 1 ORDER BY displaySender ASC")
    fun observeActive(): Flow<List<SenderProfileEntity>>

    @Query("SELECT * FROM sender_profiles ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SenderProfileEntity>

    @Query("UPDATE sender_profiles SET active = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivate(id: Long, updatedAt: Long)

    @Query("SELECT normalizedSenderKey FROM sender_profiles WHERE active = 1")
    suspend fun activeNormalizedKeys(): List<String>
}

@Dao
interface AccountSenderProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: AccountSenderProfileCrossRef): Long

    @Query("DELETE FROM account_sender_profiles WHERE accountId = :accountId AND senderProfileId = :senderProfileId")
    suspend fun delete(accountId: Long, senderProfileId: Long)

    @Query("DELETE FROM account_sender_profiles WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)

    @Query("SELECT * FROM account_sender_profiles WHERE accountId = :accountId")
    suspend fun getForAccount(accountId: Long): List<AccountSenderProfileCrossRef>

    @Query("SELECT * FROM account_sender_profiles WHERE senderProfileId = :senderProfileId")
    suspend fun getForSender(senderProfileId: Long): List<AccountSenderProfileCrossRef>

    @Query("SELECT accountId FROM account_sender_profiles WHERE senderProfileId = :senderProfileId")
    suspend fun accountIdsForSender(senderProfileId: Long): List<Long>

    @Query("SELECT senderProfileId FROM account_sender_profiles WHERE accountId = :accountId")
    suspend fun senderIdsForAccount(accountId: Long): List<Long>

    @Query(
        """
        SELECT DISTINCT sp.normalizedSenderKey FROM sender_profiles sp
        INNER JOIN account_sender_profiles asp ON asp.senderProfileId = sp.id
        INNER JOIN financial_accounts fa ON fa.id = asp.accountId
        WHERE sp.active = 1 AND fa.isActive = 1 AND fa.isOwnedByUser = 1
          AND fa.systemAccountKey IS NULL
        """,
    )
    suspend fun activeOwnedSenderKeys(): List<String>

    @Query("SELECT * FROM account_sender_profiles")
    suspend fun getAll(): List<AccountSenderProfileCrossRef>
}
