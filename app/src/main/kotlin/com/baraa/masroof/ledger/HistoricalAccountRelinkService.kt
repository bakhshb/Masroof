package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.FinancialAccountRepository
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus

/**
 * Gap-fill / rematch of historical unposted transactions against the
 * current typed identifier table.
 *
 * Safety guarantees:
 *  - Never modifies [TransactionPostingStatus.POSTED] rows
 *  - Never deletes or rewrites existing journal entries / postings
 *  - Never changes opening balances directly
 *  - Never overwrites [AccountLinkSource.USER] links
 *  - May create and post a **new** journal only when the rematch is
 *    confirmed (`needsReview = false`) and no journal exists yet
 *  - Reclassifies [FinancialTreatment.PENDING_REVIEW] via
 *    [LocalTreatmentAuditor] before account placement / posting
 */
class HistoricalAccountRelinkService(
    private val transactionRepository: TransactionRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val identifierRepository: AccountIdentifierRepository,
    private val journalGenerationService: JournalGenerationService? = null,
    private val ledgerRepository: LedgerRepository? = null,
    private val systemAccounts: (suspend (SystemAccountKey) -> Long)? = null,
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
        val posted: Int = 0,
    )

    suspend fun relinkUnposted(dryRun: Boolean = false): Result {
        identifierRepository.ensureLegacyIdentifierBackfill()
        val accounts = financialAccountRepository.getOwnedActive()
        val accountsById = accounts.associateBy { it.id }
        val all = transactionRepository.getAllNewestFirst()
        var scanned = 0
        var eligible = 0
        var updated = 0
        var linkedConfirmed = 0
        var linkedNeedsReview = 0
        var stillUnlinked = 0
        var skippedPosted = 0
        var unchanged = 0
        var posted = 0

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
            val shouldWrite = rewritten != tx ||
                (!match.needsReview && match.account != null &&
                    rewritten.financialTreatment != FinancialTreatment.PENDING_REVIEW)
            if (!shouldWrite && rewritten == tx) {
                unchanged++
                if (match.account == null) stillUnlinked++
                continue
            }
            var finalRow = rewritten
            if (!dryRun) {
                if (rewritten != tx) {
                    transactionRepository.update(rewritten.copy(updatedAt = now()))
                }
                if (!match.needsReview &&
                    match.account != null &&
                    finalRow.linkedJournalEntryId == null &&
                    finalRow.financialTreatment != FinancialTreatment.PENDING_REVIEW &&
                    finalRow.financialTreatment != FinancialTreatment.IGNORED
                ) {
                    val postedRow = tryAutoPost(finalRow, accountsById, match)
                    if (postedRow != null) {
                        finalRow = postedRow
                        posted++
                    }
                }
            }
            if (rewritten != tx || finalRow.postingStatus == TransactionPostingStatus.POSTED) {
                updated++
            } else {
                unchanged++
            }
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
            posted = posted,
        )
    }

    /**
     * Unposted, non-user rows: unlinked, tentative links, or any stuck
     * needs-review row so newly added last-fours can reclaim them.
     */
    private fun isRelinkCandidate(tx: TransactionEntity): Boolean {
        if (tx.accountLinkSource == AccountLinkSource.USER) return false
        if (tx.postingStatus == TransactionPostingStatus.POSTED || tx.linkedJournalEntryId != null) return false
        if (tx.needsReview || tx.accountLinkNeedsReview) return true
        return when (tx.accountLinkSource) {
            AccountLinkSource.UNLINKED,
            AccountLinkSource.OWNED_ACCOUNT_RULE,
            AccountLinkSource.LAST_FOUR_MATCH,
            AccountLinkSource.INSTITUTION_MATCH,
            -> true
            AccountLinkSource.USER -> false
        }
    }

    private suspend fun applyMatch(tx: TransactionEntity, match: AccountMatcher.Match): TransactionEntity {
        if (match.needsReview || match.account == null) {
            val cleared = tx.copy(
                sourceAccountId = null,
                destinationAccountId = null,
                accountLinkSource = AccountLinkSource.UNLINKED,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = true,
                needsReview = true,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                userConfirmed = false,
            )
            return if (sameLinkFields(cleared, tx)) tx else cleared
        }

        val treatment = resolveTreatment(tx)
        val accountId = match.account.id
        val sourceId = when (treatment) {
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE, FinancialTreatment.CASH_WITHDRAWAL,
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT,
            -> accountId
            else -> null
        }
        val destinationId = when (treatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> accountId
            FinancialTreatment.INTERNAL_TRANSFER, FinancialTreatment.CREDIT_CARD_PAYMENT, FinancialTreatment.INVESTMENT ->
                match.destinationAccountCandidate?.id
            else -> null
        }
        val stillNeedsReview = treatment == FinancialTreatment.PENDING_REVIEW ||
            treatment == FinancialTreatment.IGNORED ||
            (treatment.requiresTwoAccounts && (sourceId == null || destinationId == null))
        val next = tx.copy(
            financialTreatment = treatment,
            sourceAccountId = sourceId,
            destinationAccountId = destinationId,
            accountLinkSource = match.source,
            accountLinkConfidence = match.confidence,
            accountLinkNeedsReview = stillNeedsReview,
            needsReview = stillNeedsReview,
            postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
            userConfirmed = false,
            exclusionReason = if (treatment != FinancialTreatment.PENDING_REVIEW &&
                tx.exclusionReason?.contains("no rule matched") == true
            ) {
                null
            } else {
                tx.exclusionReason
            },
        )
        return if (
            sameLinkFields(next, tx) &&
            next.financialTreatment == tx.financialTreatment &&
            next.needsReview == tx.needsReview
        ) {
            tx
        } else {
            next
        }
    }

    private fun sameLinkFields(a: TransactionEntity, b: TransactionEntity): Boolean =
        a.sourceAccountId == b.sourceAccountId &&
            a.destinationAccountId == b.destinationAccountId &&
            a.accountLinkSource == b.accountLinkSource &&
            a.accountLinkNeedsReview == b.accountLinkNeedsReview &&
            a.accountLinkConfidence == b.accountLinkConfidence

    private suspend fun tryAutoPost(
        tx: TransactionEntity,
        accountsById: Map<Long, FinancialAccount>,
        match: AccountMatcher.Match,
    ): TransactionEntity? {
        val generator = journalGenerationService ?: return null
        val ledger = ledgerRepository ?: return null
        if (tx.financialTreatment == FinancialTreatment.PENDING_REVIEW ||
            tx.financialTreatment == FinancialTreatment.IGNORED
        ) {
            return null
        }
        if (tx.status != TransactionStatus.COMPLETED) return null
        val source = tx.sourceAccountId?.let { accountsById[it] }
        val destination = tx.destinationAccountId?.let { accountsById[it] }
            ?: match.destinationAccountCandidate
        val draft = generator.generate(tx, source, destination) ?: return null
        val postedDraft = draft.copy(postingStatus = JournalPostingStatus.POSTED)
        val journalId = ledger.create(postedDraft)
        if (journalId <= 0L) return null
        val posted = tx.copy(
            linkedJournalEntryId = journalId,
            postingStatus = TransactionPostingStatus.POSTED,
            needsReview = false,
            accountLinkNeedsReview = false,
            updatedAt = now(),
        )
        transactionRepository.update(posted)
        return posted
    }

    companion object {
        fun resolveTreatment(tx: TransactionEntity, smsBody: String? = null): FinancialTreatment {
            if (tx.financialTreatment != FinancialTreatment.PENDING_REVIEW) {
                return tx.financialTreatment
            }
            return LocalTreatmentAuditor.auditTransaction(tx, smsBody = smsBody).treatment
        }
    }
}
