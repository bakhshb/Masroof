package com.baraa.masroof.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * One persisted parsed bank transaction.
 *
 * The full original SMS body is intentionally NOT stored here by default; the
 * spec asks for the structured parsed fields only. The unique [uniqueFingerprint]
 * is a SHA-256 of stable transaction values (sender, timestamp, amount, currency,
 * type, merchant, last-four) and is used to deduplicate imports.
 *
 * @param id                          auto-generated local row id
 * @param uniqueFingerprint           SHA-256 hex string; unique-indexed
 * @param smsTimestamp                SMS-received epoch millis
 * @param originalSender              raw sender (preserved for matching only)
 * @param transactionType             parsed type
 * @param amount                      parsed amount as [BigDecimal]; null if absent
 * @param currency                    parsed currency
 * @param merchantOrBeneficiary       parsed merchant / beneficiary; null if absent
 * @param accountOrCardLastFourDigits parsed last-four; null if absent
 * @param transactionDate             parsed date; null if absent
 * @param transactionTime             parsed time; null if absent
 * @param status                      parsed status
 * @param confidence                  parser confidence 0..100
 * @param parsingNotes                human-readable notes (never include body / card)
 * @param dateSource                  where the date came from
 * @param createdAt                   row-created epoch millis
 * @param updatedAt                   row-updated epoch millis
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["uniqueFingerprint"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "uniqueFingerprint")
    val uniqueFingerprint: String,
    val smsTimestamp: Long,
    val originalSender: String?,
    val transactionType: TransactionType,
    val amount: BigDecimal?,
    val currency: Currency,
    val merchantOrBeneficiary: String?,
    val accountOrCardLastFourDigits: String?,
    val transactionDate: LocalDate?,
    val transactionTime: LocalTime?,
    val status: TransactionStatus,
    val confidence: Int,
    val parsingNotes: List<String>,
    val dateSource: DateSource,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Coarse fingerprint used to detect near-duplicate transactions that
     * arrive in separate SMS messages (e.g. a push + a digest). Computed
     * by [com.baraa.masroof.transaction.TransactionFingerprint.generateSimilarityKey]
     * — it deliberately excludes the exact SMS-received timestamp so two
     * SMS for the same purchase sent minutes apart still collide. The
     * importing service then compares against the [com.baraa.masroof.data.repository.TransactionImportService.DUPLICATE_WINDOW_MILLIS]
     * window before flagging as POSSIBLE_DUPLICATE.
     */
    val transactionSimilarityKey: String? = null,
    // -- v3 financial-treatment fields --------------------------------
    /** How this transaction affects the user's finances. */
    val financialTreatment: FinancialTreatment = FinancialTreatment.PENDING_REVIEW,
    /** Assigned category id (FK to [CategoryEntity.id]). Null if unclassified. */
    val categoryId: Long? = null,
    /** How the category / treatment was assigned. */
    val categorySource: CategorySource = CategorySource.UNCLASSIFIED,
    /** Confidence in the category / treatment assignment (0..100). */
    val categoryConfidence: Int = 0,
    /** True if the user must confirm before this transaction is tallied. */
    val needsReview: Boolean = true,
    /** True if the user has explicitly confirmed the assignment. */
    val userConfirmed: Boolean = false,
    /** Diagnostic reason if the transaction is excluded from spending totals. */
    val exclusionReason: String? = null,
)
