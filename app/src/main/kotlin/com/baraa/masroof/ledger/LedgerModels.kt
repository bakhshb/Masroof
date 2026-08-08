package com.baraa.masroof.ledger

import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Stable keys for hidden, non-user-editable balancing accounts. */
enum class SystemAccountKey {
    OPENING_BALANCE_EQUITY,
    EXPENSE_CLEARING,
    INCOME_CLEARING,
    BANK_FEE_EXPENSE,
    REFUND_CLEARING,
    UNASSIGNED_CLEARING,
    /** Destination for ATM / cash withdrawal journals (asset). */
    CASH_ON_HAND,
}

enum class JournalType {
    OPENING_BALANCE,
    EXPENSE,
    INCOME,
    INTERNAL_TRANSFER,
    CREDIT_CARD_PAYMENT,
    INVESTMENT_TRANSFER,
    REFUND,
    BANK_FEE,
    CASH_WITHDRAWAL,
    MANUAL_ADJUSTMENT,
    REVERSAL,
    UNASSIGNED,
}

enum class JournalPostingStatus { DRAFT, NEEDS_REVIEW, POSTED, REVERSED, VOIDED }
enum class PostingSide { DEBIT, CREDIT }
enum class JournalGeneratedBy { IMPORT_RULE, USER, MIGRATION, SYSTEM }
enum class AccountLinkSource { LAST_FOUR_MATCH, SENDER_PROFILE, INSTITUTION_MATCH, OWNED_ACCOUNT_RULE, USER, UNLINKED }
enum class AccountLinkConfidence { CONFIRMED, HIGH, MEDIUM, LOW, UNMATCHED }
enum class TransactionPostingStatus { UNPOSTED, NEEDS_REVIEW, POSTED, REVERSED, VOIDED }

/** A journal and its postings before persistence. Amounts are always positive. */
data class JournalDraft(
    val sourceTransactionId: Long?,
    val journalType: JournalType,
    val postingStatus: JournalPostingStatus,
    val effectiveDate: LocalDate,
    /** Missing source time is stored as noon local time; see AccountBalanceService. */
    val effectiveTime: LocalTime = LocalTime.NOON,
    val descriptionCode: String,
    val notes: String? = null,
    val generatedBy: JournalGeneratedBy = JournalGeneratedBy.IMPORT_RULE,
    val generationVersion: Int = 1,
    val reversalOfJournalId: Long? = null,
    val postings: List<PostingDraft>,
)

data class PostingDraft(
    val accountId: Long,
    val postingSide: PostingSide,
    val amount: BigDecimal,
    val currency: Currency,
    val memoCode: String? = null,
)

data class LedgerValidation(val valid: Boolean, val reason: String? = null) {
    companion object {
        fun valid() = LedgerValidation(true)
        fun invalid(reason: String) = LedgerValidation(false, reason)
    }
}

/** Pure invariants used by every persistence and posting path. */
object JournalValidator {
    fun validate(draft: JournalDraft, requireBalanced: Boolean): LedgerValidation {
        if (draft.postings.any { it.amount.signum() <= 0 }) return LedgerValidation.invalid("non_positive_amount")
        if (!requireBalanced) return LedgerValidation.valid()
        if (draft.postings.size < 2) return LedgerValidation.invalid("fewer_than_two_postings")
        if (draft.postings.map { it.currency }.distinct().size != 1) return LedgerValidation.invalid("mixed_currency")
        val debits = draft.postings.filter { it.postingSide == PostingSide.DEBIT }
            .fold(BigDecimal.ZERO) { total, posting -> total.add(posting.amount) }
        val credits = draft.postings.filter { it.postingSide == PostingSide.CREDIT }
            .fold(BigDecimal.ZERO) { total, posting -> total.add(posting.amount) }
        return if (debits.compareTo(credits) == 0) LedgerValidation.valid()
        else LedgerValidation.invalid("unbalanced")
    }
}
