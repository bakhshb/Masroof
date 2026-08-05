package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionType

/**
 * Coordinates account linking and review-only journal creation.
 * Uses [AccountIdentifierRepository] as the source of truth for typed
 * identifiers and falls back to learned rules only when no typed identifier
 * matches.
 */
class TransactionLinkingService(
    private val transactions: TransactionRepository,
    private val ledger: LedgerRepository,
    private val generator: JournalGenerationService,
    private val identifierRepository: AccountIdentifierRepository,
    private val rules: AccountLinkRuleRepository? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun applyUserLink(
        transaction: TransactionEntity,
        sourceAccountId: Long?,
        destinationAccountId: Long?,
        accounts: List<FinancialAccount>,
        rememberForFuture: Boolean = false,
        proposedAccountId: Long? = null,
        identifierToAdd: IdentifierCandidate? = null,
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
        val preferred = source ?: destination
        val draft = generator.generate(linked, source, destination) ?: return linked
        val journalId = if (transaction.linkedJournalEntryId == null) ledger.create(draft)
        else ledger.regenerateDraft(transaction.id, draft)
        if (rememberForFuture && preferred != null) {
            runCatching { rules?.remember(linked, preferred, if (preferred == source) "source" else "destination") }
        }
        identifierToAdd?.let { candidate ->
            val accountId = preferred?.id ?: sourceAccountId ?: destinationAccountId ?: return@let
            runCatching {
                identifierRepository.addOrUpdate(
                    accountId = accountId,
                    form = com.baraa.masroof.data.repository.IdentifierForm(
                        identifierType = candidate.identifierType,
                        displayLabel = displayLabelFor(candidate),
                        rawValue = candidate.normalizedLastFour,
                    ),
                )
            }
        }
        return linked.copy(linkedJournalEntryId = journalId, updatedAt = now()).also { transactions.update(it) }
    }

    private fun displayLabelFor(candidate: IdentifierCandidate): String = when (candidate.identifierType) {
        AccountIdentifierType.ACCOUNT_LAST4 -> "حساب"
        AccountIdentifierType.CREDIT_CARD_LAST4 -> "بطاقة ائتمانية"
        AccountIdentifierType.DEBIT_CARD_LAST4 -> "بطاقة مدى"
        AccountIdentifierType.IBAN_LAST4 -> "آيبان"
        AccountIdentifierType.WALLET_LAST4 -> "محفظة"
        AccountIdentifierType.SENDER_ALIAS -> "مرسل"
    }

    suspend fun linkAndGenerate(transaction: TransactionEntity, accounts: List<FinancialAccount>, trackingStartDate: Long? = null): TransactionEntity {
        if (transaction.postingStatus == TransactionPostingStatus.POSTED || transaction.linkedJournalEntryId != null) return transaction
        // Pre-tracking-start transactions are preserved but never auto-posted.
        val beforeStart = trackingStartDate
            ?.let { start -> java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            ?.let { tracking -> transaction.transactionDate != null && transaction.transactionDate.isBefore(tracking) }
            ?: false
        if (beforeStart) {
            val kept = transaction.copy(
                needsReview = true,
                userConfirmed = false,
                exclusionReason = transaction.exclusionReason ?: "عملية قبل تاريخ بداية المتابعة",
                postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
                updatedAt = now(),
            )
            transactions.update(kept)
            return kept
        }
        val direct = AccountMatcher.match(transaction, accounts, identifierRepository)
        val remembered = if (direct.level == AccountLinkConfidence.UNMATCHED) rules?.find(transaction, accounts) else null
        val match = if (remembered == null) direct else AccountMatcher.Match(
            account = remembered,
            source = AccountLinkSource.OWNED_ACCOUNT_RULE,
            confidence = 80,
            needsReview = true,
            level = AccountLinkConfidence.MEDIUM,
            diagnosticCode = "learned_rule",
        )
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

    /**
     * Identifies whether the two parsed accounts can be classified as
     * INTERNAL_TRANSFER. Only safe when both accounts are owned by the user.
     */
    fun resolveTransferTreatment(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
    ): FinancialTreatment {
        val source = accounts.firstOrNull { it.id == transaction.sourceAccountId }
        val destination = accounts.firstOrNull { it.id == transaction.destinationAccountId }
        return when {
            source != null && destination != null && source.isOwnedByUser && destination.isOwnedByUser -> FinancialTreatment.INTERNAL_TRANSFER
            else -> transaction.financialTreatment
        }
    }
}
