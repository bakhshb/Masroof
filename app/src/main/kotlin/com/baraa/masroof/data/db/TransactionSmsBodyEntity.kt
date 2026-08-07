package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Local-only copy of the bank SMS body for on-device link assist.
 * Never logged, never sent off-device. Cascades when the transaction is deleted.
 */
@Entity(
    tableName = "transaction_sms_bodies",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TransactionSmsBodyEntity(
    @PrimaryKey
    @ColumnInfo(name = "transactionId")
    val transactionId: Long,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
)

@Dao
interface TransactionSmsBodyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TransactionSmsBodyEntity)

    @Query("SELECT * FROM transaction_sms_bodies WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getByTransactionId(transactionId: Long): TransactionSmsBodyEntity?

    @Query("DELETE FROM transaction_sms_bodies WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)
}
