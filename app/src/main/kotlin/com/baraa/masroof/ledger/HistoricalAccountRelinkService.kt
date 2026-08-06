package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.FinancialAccountRepository
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment

/**
 * Opt-in gap-fill re-linking of historical transactions against the
 * current typed identifier table.
 *
 * Safety guarantees:
 *  - Never modifies [TransactionPostingStatus.POSTED] rows
 *  - Never deletes or rewrites journal entries / postings
 *  - Never changes opening balances or calculated balances directly
 *  - Only gap-fills UNLINKED / both-account-null rows
 *  - Never overwrites USER links or existing proposed account IDs
 *  - Leaves rows in [TransactionPostingStatus.NEEDS_REVIEW] for user confirmation
 *  - Does not auto-create or auto-post journals
 */
class HistoricalAccountRelinkService(
    private val transactionRepository: TransactionRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val identifierRepository: AccountIdentifierRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    data class Result(
        val scanned: Int = 0,
        val eligible: Int = 0,
        val updated: Int = 0,
        val linkedConfirmed: Int = 0,
        val linkedNeedsReview: Int = 0,
        val stillUnlinked: Int = 0,
        val skippedPosted: Int = 0,
        val unchanged: Int = 0,
    )

    suspend fun relinkUnposted(dryRun: Boolean = false): Result {
        identifierRepository.ensureLegacyIdentifierBackfill()
        val accounts = financialAccountRepository.getOwnedActive()
        val all = transactionRepository.getAllNewestFirst()
        var scanned = 0
        var eligible = 0
        var updated = 0
        var linkedConfirmed = 0
        var linkedNeedsReview = 0
        var stillUnlinked = 0
        var skippedPosted = 0
        var unchanged = 0

        for (tx in all) {
            scanned++
            if (tx.postingStatus == TransactionPostingStatus.POSTED || tx.linkedJournalEntryId != null) {
                skippedPosted++
                continue
            }
            if (!isRelinkCandidate(tx)) {
                unchanged++
                continue
            }
            eligible++
            val match = AccountMatcher.match(tx, accounts, identifierRepository)
            val rewritten = applyMatch(tx, match)
            if (rewritten == tx) {
                unchanged++
                if (match.account == null) stillUnlinked++
                continue
            }
            if (!dryRun) {
                transactionRepository.update(rewritten.copy(updatedAt = now()))
            }
            updated++
            when {
                match.account == null -> stillUnlinked++
                match.needsReview -> linkedNeedsReview++
                else -> linkedConfirmed++
            }
        }
        return Result(
            scanned = scanned,
            eligible = eligible,
            updated = updated,
            linkedConfirmed = linkedConfirmed,
            linkedNeedsReview = linkedNeedsReview,
            stillUnlinked = stillUnlinked,
            skippedPosted = skippedPosted,
            unchanged = unchanged,
        )
    }

    /**
     * Gap-fill only: rows with no account link yet. Existing proposals
     * (including needsReview proposals) are left for the review queue.
     */
    private fun isRelinkCandidate(tx: TransactionEntity): Boolean {
        if (tx.accountLinkSource == AccountLinkSource.USER) return false
        if (tx.sourceAccountId != null || tx.destinationAccountId != null) return false
        return tx.accountLinkSource == AccountLinkSource.UNLINKED
    }

    private fun applyMatch(tx: TransactionEntity, match: AccountMatcher.Match): TransactionEntity {
        val accountId = match.account?.id
        val sourceId = when (tx.financialTreatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL,
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT,
            -> accountId
            else -> null
        }
        val destinationId = when (tx.financialTreatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> accountId
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT ->
                match.destinationAccountCandidate?.id
            else -> null
        }
        val next = tx.copy(
            sourceAccountId = sourceId,
            destinationAccountId = destinationId,
            accountLinkSource = match.source,
            accountLinkConfidence = match.confidence,
            accountLinkNeedsReview = match.needsReview || match.account == null,
            needsReview = true,
            postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
            userConfirmed = false,
        )
        return if (
            next.sourceAccountId == tx.sourceAccountId &&
            next.destinationAccountId == tx.destinationAccountId &&
            next.accountLinkSource == tx.accountLinkSource &&
            next.accountLinkConfidence == tx.accountLinkConfidence &&
            next.accountLinkNeedsReview == tx.accountLinkNeedsReview
        ) {
            tx
        } else {
            next
        }
    }
}
