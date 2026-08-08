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

/**
 * User-facing logical group of structural SMS variants for one sender.
 *
 * A family is intentionally not a transaction type and never has an account
 * reference. Its variants carry the exact structure and field definitions.
 */
@Entity(
    tableName = "message_pattern_families",
    indices = [
        Index(value = ["senderProfileId"]),
        Index(value = ["senderProfileId", "stableKey"], unique = true),
        Index(value = ["status"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SenderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessagePatternFamilyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderProfileId: Long,
    /** Stable structural grouping hint, never a TransactionType enum value. */
    val stableKey: String,
    val displayName: String,
    val status: MessagePatternStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Required and optional static anchors for a PatternVariant. The label is
 * normalized structural text only; it contains no customer values.
 */
@Entity(
    tableName = "pattern_variant_anchors",
    indices = [
        Index(value = ["variantId"]),
        Index(value = ["variantId", "normalizedAnchor"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MessagePatternDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PatternVariantAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val variantId: Long,
    @ColumnInfo(name = "normalizedAnchor") val normalizedAnchor: String,
    val required: Boolean,
)

@Dao
interface MessagePatternFamilyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: MessagePatternFamilyEntity): Long

    @Update
    suspend fun update(row: MessagePatternFamilyEntity)

    @Query("SELECT * FROM message_pattern_families WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessagePatternFamilyEntity?

    @Query("SELECT * FROM message_pattern_families WHERE senderProfileId = :senderProfileId ORDER BY updatedAt DESC")
    suspend fun getForSender(senderProfileId: Long): List<MessagePatternFamilyEntity>

    @Query("SELECT * FROM message_pattern_families WHERE senderProfileId = :senderProfileId AND stableKey = :stableKey LIMIT 1")
    suspend fun findByStableKey(senderProfileId: Long, stableKey: String): MessagePatternFamilyEntity?
}

@Dao
interface PatternVariantAnchorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PatternVariantAnchorEntity>)

    @Query("SELECT * FROM pattern_variant_anchors WHERE variantId = :variantId")
    suspend fun getForVariant(variantId: Long): List<PatternVariantAnchorEntity>

    @Query("DELETE FROM pattern_variant_anchors WHERE variantId = :variantId")
    suspend fun deleteForVariant(variantId: Long)
}
