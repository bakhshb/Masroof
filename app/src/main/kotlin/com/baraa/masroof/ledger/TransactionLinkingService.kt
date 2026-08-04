package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment

/** Coordinates conservative account linking and review-only journal creation. */
class TransactionLinkingService(
    private val transactions: TransactionRepository,
    private val ledger: LedgerRepository,
    private val generator: JournalGenerationService,
    private val rules: AccountLinkRuleRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Applies an explicit user-selected source/destination pair, then creates a review-only journal. */
    suspend fun applyUserLink(
        transaction: TransactionEntity,
        sourceAccountId: Long?,
        destinationAccountId: Long?,
        accounts: List<FinancialAccount>,
        rememberForFuture: Boolean = false,
        proposedAccountId: Long? = null,
    ): TransactionEntity {
        require(transaction.postingStatus != TransactionPostingStatus.POSTED) { "posted_transaction_requires_correction" }
        val linked = transaction.copy(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            accountLinkSource = AccountLinkSource.USER,
            accountLinkConfidence = 100,
            accountLinkNeedsReview = false,
            postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
            updatedAt = now(),
        )
        transactions.update(linked)
        val source = accounts.firstOrNull { it.id == sourceAccountId }
        val destination = accounts.firstOrNull { it.id == destinationAccountId }
        val draft = generator.generate(linked, source, destination) ?: return linked
        val journalId = if (transaction.linkedJournalEntryId == null) ledger.create(draft)
        else ledger.regenerateDraft(transaction.id, draft)
        if (rememberForFuture) {
            val preferred = accounts.firstOrNull { it.id == proposedAccountId } ?: (source ?: destination)
            if (preferred != null) runCatching { rules?.remember(linked, preferred, if (preferred == source) "source" else "destination") }
        }
        return linked.copy(linkedJournalEntryId = journalId, updatedAt = now()).also { transactions.update(it) }
    }

    suspend fun linkAndGenerate(transaction: TransactionEntity, accounts: List<FinancialAccount>): TransactionEntity {
        if (transaction.postingStatus == TransactionPostingStatus.POSTED || transaction.linkedJournalEntryId != null) return transaction
        val direct = AccountMatcher.match(transaction, accounts)
        val remembered = if (direct.level == AccountLinkConfidence.UNMATCHED) rules?.find(transaction, accounts) else null
        val match = if (remembered == null) direct else AccountMatcher.Match(remembered, AccountLinkSource.OWNED_ACCOUNT_RULE, 85, false, AccountLinkConfidence.HIGH, "learned_rule")
        val linked = when (transaction.financialTreatment) {
            FinancialTreatment.INCOME, FinancialTreatment.REFUND -> transaction.copy(
                destinationAccountId = match.account?.id,
                sourceAccountId = null,
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            FinancialTreatment.EXPENSE, FinancialTreatment.BANK_FEE -> transaction.copy(
                sourceAccountId = match.account?.id,
                destinationAccountId = null,
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            else -> transaction.copy(
                accountLinkSource = match.source,
                accountLinkConfidence = match.confidence,
                accountLinkNeedsReview = match.needsReview,
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
        }
        transactions.update(linked)
        val source = accounts.firstOrNull { it.id == linked.sourceAccountId }
        val destination = accounts.firstOrNull { it.id == linked.destinationAccountId }
        generator.generate(linked, source, destination)?.let { draft ->
            val journalId = ledger.create(draft)
            val withJournal = linked.copy(linkedJournalEntryId = journalId, updatedAt = now())
            transactions.update(withJournal)
            return withJournal
        }
        return linked
    }
}
