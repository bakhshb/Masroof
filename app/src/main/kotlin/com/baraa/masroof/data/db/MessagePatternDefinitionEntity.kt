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

enum class MessagePatternStatus {
    APPROVED,
    IGNORED,
    UNKNOWN,
    DEPRECATED,
}

enum class PatternOrigin {
    USER_TRAINED,
    MIGRATED,
    BUILT_IN,
    AI_SUGGESTED,
}

enum class PatternCanonicalField {
    TRANSACTION_AMOUNT,
    CURRENCY,
    MERCHANT,
    TRANSACTION_DATE,
    TRANSACTION_TIME,
    ACCOUNT_LAST4,
    SOURCE_ACCOUNT_LAST4,
    DESTINATION_ACCOUNT_LAST4,
    CREDIT_CARD_LAST4,
    DEBIT_CARD_LAST4,
    IBAN_LAST4,
    SOURCE_IBAN_LAST4,
    DESTINATION_IBAN_LAST4,
    WALLET_LAST4,
    AVAILABLE_BALANCE,
    CARD_AMOUNT_DUE,
    TRANSACTION_REFERENCE,
    SOURCE_INSTITUTION,
    DESTINATION_INSTITUTION,
    BENEFICIARY,
    CHANNEL,
}

enum class PatternFieldRole {
    PRIMARY,
    SOURCE,
    DESTINATION,
    CONTEXT,
}

enum class PatternValueType {
    MONEY,
    LAST4,
    TEXT,
    DATE,
    TIME,
    CURRENCY_CODE,
    REFERENCE,
}

enum class PatternExtractionStrategy {
    LABELED_LINE,
    INLINE_AFTER_LABEL,
    FIRST_MATCH,
}

/**
 * Structural SMS message style for a SenderProfile.
 * Never stores raw SMS bodies or personal values.
 */
@Entity(
    tableName = "message_pattern_definitions",
    indices = [
        Index(value = ["senderProfileId"]),
        Index(value = ["senderProfileId", "normalizedSignature", "version"], unique = true),
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
data class MessagePatternDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "senderProfileId")
    val senderProfileId: Long,
    @ColumnInfo(name = "userFriendlyName")
    val userFriendlyName: String,
    @ColumnInfo(name = "normalizedSignature")
    val normalizedSignature: String,
    @ColumnInfo(name = "transactionType")
    val transactionType: String? = null,
    @ColumnInfo(name = "direction")
    val direction: String? = null,
    @ColumnInfo(name = "channel")
    val channel: String? = null,
    @ColumnInfo(name = "status")
    val status: MessagePatternStatus,
    @ColumnInfo(name = "version")
    val version: Int = 1,
    @ColumnInfo(name = "origin")
    val origin: PatternOrigin = PatternOrigin.USER_TRAINED,
    @ColumnInfo(name = "confidence")
    val confidence: Int = 0,
    @ColumnInfo(name = "userConfirmed")
    val userConfirmed: Boolean = false,
    @ColumnInfo(name = "exampleCount")
    val exampleCount: Int = 1,
    @ColumnInfo(name = "activeFrom")
    val activeFrom: Long? = null,
    @ColumnInfo(name = "deprecatedAt")
    val deprecatedAt: Long? = null,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,
)

/**
 * Label → canonical field mapping for a pattern. Stores labels only.
 */
@Entity(
    tableName = "pattern_field_definitions",
    indices = [
        Index(value = ["patternId"]),
        Index(value = ["patternId", "canonicalField", "sourceLabel"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MessagePatternDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PatternFieldDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "patternId")
    val patternId: Long,
    @ColumnInfo(name = "canonicalField")
    val canonicalField: PatternCanonicalField,
    @ColumnInfo(name = "sourceLabel")
    val sourceLabel: String,
    @ColumnInfo(name = "extractionStrategy")
    val extractionStrategy: PatternExtractionStrategy = PatternExtractionStrategy.LABELED_LINE,
    @ColumnInfo(name = "required")
    val required: Boolean = false,
    @ColumnInfo(name = "role")
    val role: PatternFieldRole = PatternFieldRole.PRIMARY,
    @ColumnInfo(name = "valueType")
    val valueType: PatternValueType = PatternValueType.TEXT,
)

@Dao
interface MessagePatternDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: MessagePatternDefinitionEntity): Long

    @Update
    suspend fun update(row: MessagePatternDefinitionEntity)

    @Query("SELECT * FROM message_pattern_definitions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessagePatternDefinitionEntity?

    @Query("SELECT * FROM message_pattern_definitions WHERE senderProfileId = :senderProfileId ORDER BY updatedAt DESC")
    suspend fun getForSender(senderProfileId: Long): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE senderProfileId = :senderProfileId AND status = :status
        ORDER BY updatedAt DESC
        """,
    )
    suspend fun getForSenderByStatus(
        senderProfileId: Long,
        status: MessagePatternStatus,
    ): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE senderProfileId = :senderProfileId AND normalizedSignature = :signature
        ORDER BY version DESC LIMIT 1
        """,
    )
    suspend fun findLatestBySignature(
        senderProfileId: Long,
        signature: String,
    ): MessagePatternDefinitionEntity?

    @Query("SELECT * FROM message_pattern_definitions WHERE status = :status")
    suspend fun getByStatus(status: MessagePatternStatus): List<MessagePatternDefinitionEntity>

    @Query("SELECT * FROM message_pattern_definitions WHERE status = 'UNKNOWN' ORDER BY updatedAt DESC")
    fun observeUnknown(): Flow<List<MessagePatternDefinitionEntity>>

    @Query("SELECT COUNT(*) FROM message_pattern_definitions WHERE status = 'UNKNOWN'")
    suspend fun countUnknown(): Int

    @Query("SELECT * FROM message_pattern_definitions WHERE status IN ('APPROVED', 'DEPRECATED')")
    suspend fun getImportable(): List<MessagePatternDefinitionEntity>
}

@Dao
interface PatternFieldDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: PatternFieldDefinitionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PatternFieldDefinitionEntity>)

    @Query("SELECT * FROM pattern_field_definitions WHERE patternId = :patternId")
    suspend fun getForPattern(patternId: Long): List<PatternFieldDefinitionEntity>

    @Query("DELETE FROM pattern_field_definitions WHERE patternId = :patternId")
    suspend fun deleteForPattern(patternId: Long)
}
