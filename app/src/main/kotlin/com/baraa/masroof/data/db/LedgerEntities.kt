package com.baraa.masroof.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.PrimaryKey
import com.baraa.masroof.ledger.JournalGeneratedBy
import com.baraa.masroof.ledger.JournalPostingStatus
import com.baraa.masroof.ledger.JournalType
import com.baraa.masroof.ledger.PostingSide
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTransactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sourceTransactionId"]),
        Index(value = ["postingStatus"]),
        Index(value = ["effectiveDate", "effectiveTime"]),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTransactionId: Long?,
    val journalType: JournalType,
    val postingStatus: JournalPostingStatus,
    val effectiveDate: LocalDate,
    val effectiveTime: LocalTime,
    val descriptionCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val reversalOfJournalId: Long? = null,
    val notes: String? = null,
    val generatedBy: JournalGeneratedBy,
    val generationVersion: Int,
)

@Entity(
    tableName = "ledger_postings",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FinancialAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["journalEntryId"]), Index(value = ["accountId"])],
)
data class LedgerPostingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalEntryId: Long,
    val accountId: Long,
    val postingSide: PostingSide,
    val amount: BigDecimal,
    val currency: Currency,
    val memoCode: String? = null,
    val createdAt: Long,
)

data class JournalWithPostings(
    @Embedded val journal: JournalEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "journalEntryId")
    val postings: List<LedgerPostingEntity>,
)
