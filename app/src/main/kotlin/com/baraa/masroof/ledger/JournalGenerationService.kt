package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Generates review-only journal proposals; it never posts a financial journal. */
class JournalGenerationService(
    private val systemAccounts: suspend (SystemAccountKey) -> Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun generate(
        transaction: TransactionEntity,
        source: FinancialAccount?,
        destination: FinancialAccount?,
    ): JournalDraft? {
        val amount = transaction.amount ?: return null
        if (amount.signum() <= 0 || transaction.status != TransactionStatus.COMPLETED) return null
        val date = transaction.transactionDate ?: Instant.ofEpochMilli(transaction.smsTimestamp).atZone(zoneId).toLocalDate()
        val time = transaction.transactionTime ?: LocalTime.NOON
        val currency = transaction.currency
        fun draft(type: JournalType, lines: List<PostingDraft>) = JournalDraft(
            sourceTransactionId = transaction.id,
            journalType = type,
            postingStatus = JournalPostingStatus.NEEDS_REVIEW,
            effectiveDate = date,
            effectiveTime = time,
            descriptionCode = type.name.lowercase(),
            generatedBy = JournalGeneratedBy.IMPORT_RULE,
            postings = lines,
        )
        suspend fun system(key: SystemAccountKey) = systemAccounts(key)
        return when (transaction.financialTreatment) {
            FinancialTreatment.EXPENSE -> source?.let {
                draft(JournalType.EXPENSE, listOf(
                    PostingDraft(system(SystemAccountKey.EXPENSE_CLEARING), PostingSide.DEBIT, amount, currency),
                    PostingDraft(it.id, PostingSide.CREDIT, amount, currency),
                ))
            }
            FinancialTreatment.INCOME -> destination?.let {
                draft(JournalType.INCOME, listOf(
                    PostingDraft(it.id, PostingSide.DEBIT, amount, currency),
                    PostingDraft(system(SystemAccountKey.INCOME_CLEARING), PostingSide.CREDIT, amount, currency),
                ))
            }
            FinancialTreatment.INTERNAL_TRANSFER -> if (source != null && destination != null) draft(JournalType.INTERNAL_TRANSFER, listOf(
                PostingDraft(destination.id, PostingSide.DEBIT, amount, currency),
                PostingDraft(source.id, PostingSide.CREDIT, amount, currency),
            )) else null
            FinancialTreatment.CREDIT_CARD_PAYMENT -> if (source != null && destination != null) draft(JournalType.CREDIT_CARD_PAYMENT, listOf(
                PostingDraft(destination.id, PostingSide.DEBIT, amount, currency),
                PostingDraft(source.id, PostingSide.CREDIT, amount, currency),
            )) else null
            FinancialTreatment.INVESTMENT -> if (source != null && destination != null) draft(JournalType.INVESTMENT_TRANSFER, listOf(
                PostingDraft(destination.id, PostingSide.DEBIT, amount, currency),
                PostingDraft(source.id, PostingSide.CREDIT, amount, currency),
            )) else null
            FinancialTreatment.REFUND -> (destination ?: source)?.let {
                val side = if (it.accountNature == com.baraa.masroof.transaction.AccountNature.LIABILITY) PostingSide.DEBIT else PostingSide.DEBIT
                draft(JournalType.REFUND, listOf(
                    PostingDraft(it.id, side, amount, currency),
                    PostingDraft(system(SystemAccountKey.REFUND_CLEARING), PostingSide.CREDIT, amount, currency),
                ))
            }
            FinancialTreatment.BANK_FEE -> source?.let {
                draft(JournalType.BANK_FEE, listOf(
                    PostingDraft(system(SystemAccountKey.BANK_FEE_EXPENSE), PostingSide.DEBIT, amount, currency),
                    PostingDraft(it.id, PostingSide.CREDIT, amount, currency),
                ))
            }
            FinancialTreatment.CASH_WITHDRAWAL -> source?.let {
                // Same balance effect as spending from the bank/salary account —
                // no separate cash-on-hand destination is required.
                draft(JournalType.CASH_WITHDRAWAL, listOf(
                    PostingDraft(system(SystemAccountKey.EXPENSE_CLEARING), PostingSide.DEBIT, amount, currency),
                    PostingDraft(it.id, PostingSide.CREDIT, amount, currency),
                ))
            }
            FinancialTreatment.PENDING_REVIEW, FinancialTreatment.IGNORED -> null
        }
    }
}
