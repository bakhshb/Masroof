package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * How a taught SMS pattern should be applied for a sender.
 *
 * INCLUDE_TRANSACTION — treat similar SMS as financial candidates.
 * IGNORE_AUTH — treat matching SMS as OTP / verification (never import).
 */
enum class SenderMessagePatternKind {
    INCLUDE_TRANSACTION,
    IGNORE_AUTH,
}

/**
 * Structural SMS style learned for a sender (transfer, Google Pay, debit, …).
 * Belongs to the sender only — accounts link to senders separately via identifiers.
 * Stores labels and cues only — never the raw SMS body.
 */
@Entity(
    tableName = "sender_message_patterns",
    indices = [
        Index(value = ["senderKey"]),
        Index(value = ["accountId"]),
        Index(value = ["senderKey", "structureKey", "kind"], unique = true),
    ],
)
data class SenderMessagePatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "senderKey")
    val senderKey: String,
    /**
     * Stable fingerprint of sorted normalized line labels for this style.
     * Multiple INCLUDE rows per sender are allowed (one per structureKey).
     */
    @ColumnInfo(name = "structureKey")
    val structureKey: String,
    /**
     * Legacy optional account link. New rows leave this null; account matching
     * uses last4 identifiers on the message, not the pattern.
     */
    @ColumnInfo(name = "accountId")
    val accountId: Long? = null,
    @ColumnInfo(name = "kind")
    val kind: SenderMessagePatternKind,
    /** Amount-bearing line labels learned from INCLUDE examples. */
    @ColumnInfo(name = "amountLabels")
    val amountLabels: List<String> = emptyList(),
    /** Type cue phrases seen in INCLUDE examples. */
    @ColumnInfo(name = "typeCues")
    val typeCues: List<String> = emptyList(),
    /** Structural line labels from examples. */
    @ColumnInfo(name = "lineLabels")
    val lineLabels: List<String> = emptyList(),
    @ColumnInfo(name = "minScore")
    val minScore: Int = 1,
    @ColumnInfo(name = "exampleCount")
    val exampleCount: Int = 1,
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

@Dao
interface SenderMessagePatternDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: SenderMessagePatternEntity): Long

    @Update
    suspend fun update(row: SenderMessagePatternEntity)

    @Query(
        """
        SELECT * FROM sender_message_patterns
        WHERE senderKey = :senderKey AND structureKey = :structureKey AND kind = :kind
        LIMIT 1
        """,
    )
    suspend fun find(
        senderKey: String,
        structureKey: String,
        kind: SenderMessagePatternKind,
    ): SenderMessagePatternEntity?

    @Query("SELECT * FROM sender_message_patterns WHERE active = 1")
    suspend fun getActive(): List<SenderMessagePatternEntity>

    @Query("SELECT * FROM sender_message_patterns WHERE active = 1 AND kind = :kind")
    suspend fun getActiveByKind(kind: SenderMessagePatternKind): List<SenderMessagePatternEntity>

    @Query(
        """
        SELECT * FROM sender_message_patterns
        WHERE active = 1 AND kind = :kind AND senderKey = :senderKey
        """,
    )
    suspend fun getActiveBySenderAndKind(
        senderKey: String,
        kind: SenderMessagePatternKind,
    ): List<SenderMessagePatternEntity>

    @Query("SELECT DISTINCT senderKey FROM sender_message_patterns WHERE active = 1 AND kind = 'INCLUDE_TRANSACTION'")
    suspend fun activeIncludeSenderKeys(): List<String>

    @Query("SELECT * FROM sender_message_patterns WHERE active = 1 ORDER BY updatedAt DESC")
    suspend fun observeAllActive(): List<SenderMessagePatternEntity>

    @Query("UPDATE sender_message_patterns SET active = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivate(id: Long, updatedAt: Long)

    @Query("DELETE FROM sender_message_patterns WHERE id = :id")
    suspend fun delete(id: Long)
}
