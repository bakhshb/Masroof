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
    /** ApprovedTemplate — used for production SMS import matching. */
    APPROVED,
    /** Permanently ignored structure (future SMS skipped). */
    IGNORED,
    /**
     * CandidatePattern — discovered / trained but not yet accepted.
     * Never used for production matching until approved.
     */
    UNKNOWN,
    /** Superseded revision or draft lineage. */
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
        Index(value = ["senderProfileId", "canonicalKey", "version"], unique = true),
        Index(value = ["familyId"]),
        Index(value = ["lineageId"]),
        Index(value = ["status"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SenderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessagePatternFamilyEntity::class,
            parentColumns = ["id"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class MessagePatternDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "senderProfileId")
    val senderProfileId: Long,
    /** Nullable only for pre-v28 records while a safe migration backfills families. */
    @ColumnInfo(name = "familyId")
    val familyId: Long? = null,
    @ColumnInfo(name = "userFriendlyName")
    val userFriendlyName: String,
    @ColumnInfo(name = "normalizedSignature")
    val normalizedSignature: String,
    /** Canonical semantic identity shared by all revisions of one template family. */
    @ColumnInfo(name = "canonicalKey", defaultValue = "")
    val canonicalKey: String = "",
    /** Stable revision lineage. Existing rows are backfilled to their original id. */
    @ColumnInfo(name = "lineageId", defaultValue = "0")
    val lineageId: Long = 0,
    /** Human-readable structural template with {PLACEHOLDER} tokens. Null for legacy rows. */
    @ColumnInfo(name = "templateText")
    val templateText: String? = null,
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
    /** Independent matching switch. Approval and activity are separate decisions. */
    @ColumnInfo(name = "isActive", defaultValue = "0")
    val isActive: Boolean = false,
    @ColumnInfo(name = "origin")
    val origin: PatternOrigin = PatternOrigin.USER_TRAINED,
    @ColumnInfo(name = "confidence")
    val confidence: Int = 0,
    @ColumnInfo(name = "userConfirmed")
    val userConfirmed: Boolean = false,
    @ColumnInfo(name = "exampleCount")
    val exampleCount: Int = 1,
    /**
     * Version of the canonical normalizer that produced this pattern's
     * [normalizedSignature]. Patterns stamped with a version other than
     * [com.baraa.masroof.sms.NORMALIZATION_VERSION] are excluded from runtime
     * matching and surfaced as STALE so the user can rebuild.
     *
     * Default is the current [com.baraa.masroof.sms.NORMALIZATION_VERSION]
     * so newly persisted patterns always participate. The v28 → v29
     * migration explicitly sets existing rows to 0 (= STALE) so the user
     * rebuilds them under the new normalizer.
     */
    @ColumnInfo(name = "normalizationVersion", defaultValue = "2")
    val normalizationVersion: Int = com.baraa.masroof.sms.NORMALIZATION_VERSION,
    /** Approval/revision metadata; not compared with the historical SMS event time. */
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
    /** Placeholder token in templateText, without braces (for example AMOUNT). */
    @ColumnInfo(name = "placeholderToken", defaultValue = "")
    val placeholderToken: String = "",
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

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE lineageId = :lineageId OR (lineageId = 0 AND id = :lineageId)
        ORDER BY version DESC
        """,
    )
    suspend fun getByLineage(lineageId: Long): List<MessagePatternDefinitionEntity>

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
        WHERE senderProfileId = :senderProfileId AND canonicalKey = :canonicalKey
        ORDER BY version DESC LIMIT 1
        """,
    )
    suspend fun findByCanonicalKey(
        senderProfileId: Long,
        canonicalKey: String,
    ): MessagePatternDefinitionEntity?

    @Query("SELECT * FROM message_pattern_definitions WHERE senderProfileId = :senderProfileId AND normalizedSignature = :signature ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByExactSignature(
        senderProfileId: Long,
        signature: String,
    ): MessagePatternDefinitionEntity?

    @Query("SELECT * FROM message_pattern_definitions WHERE familyId = :familyId ORDER BY updatedAt DESC")
    suspend fun getForFamily(familyId: Long): List<MessagePatternDefinitionEntity>

    @Query("SELECT * FROM message_pattern_definitions WHERE status = :status")
    suspend fun getByStatus(status: MessagePatternStatus): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE status = 'UNKNOWN' AND deprecatedAt IS NULL
        ORDER BY updatedAt DESC
        """,
    )
    fun observeUnknown(): Flow<List<MessagePatternDefinitionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM message_pattern_definitions
        WHERE status = 'UNKNOWN' AND deprecatedAt IS NULL
        """,
    )
    suspend fun countUnknown(): Int

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE status = 'APPROVED' AND isActive = 1 AND deprecatedAt IS NULL
        """,
    )
    suspend fun getImportable(): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE senderProfileId = :senderProfileId
          AND status = 'APPROVED'
          AND isActive = 1
          AND deprecatedAt IS NULL
          AND normalizationVersion = :version
        ORDER BY version DESC
        """,
    )
    suspend fun getEffectiveForSenderAtVersion(
        senderProfileId: Long,
        version: Int,
    ): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE status = 'APPROVED'
          AND isActive = 1
          AND deprecatedAt IS NULL
          AND normalizationVersion = :version
        """,
    )
    suspend fun getImportableAtVersion(version: Int): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE senderProfileId = :senderProfileId
          AND status = 'APPROVED'
          AND isActive = 1
          AND deprecatedAt IS NULL
        ORDER BY version DESC
        """,
    )
    suspend fun getEffectiveForSender(
        senderProfileId: Long,
    ): List<MessagePatternDefinitionEntity>

    @Query(
        """
        SELECT * FROM message_pattern_definitions
        WHERE templateText IS NULL OR length(trim(templateText)) = 0
        ORDER BY updatedAt DESC
        """,
    )
    suspend fun getSignatureOnly(): List<MessagePatternDefinitionEntity>
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
