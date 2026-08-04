package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountBalanceService
import com.baraa.masroof.ledger.JournalValidator
import com.baraa.masroof.ledger.LedgerRepository
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.TransactionStatus
import java.math.BigDecimal

/** Pure batch validation: each transaction must independently pass before it is committed. */
object TransactionBatchReview {
    data class ItemResult(val transaction: TransactionEntity, val valid: Boolean, val reason: String?)
    data class BatchOutcome(val confirmed: Int, val failed: Int, val reviewRequired: Int, val issues: List<String>)

    /** Validates one transaction against the local state without performing a database write. */
    fun validateOne(transaction: TransactionEntity, accounts: List<FinancialAccount>): ItemResult {
        val amount = transaction.amount
        if (amount == null || amount.signum() <= 0) return ItemResult(transaction, false, "unreliable_amount")
        if (transaction.status == TransactionStatus.DECLINED) return ItemResult(transaction, false, "declined")
        if (transaction.status == TransactionStatus.PENDING) return ItemResult(transaction, false, "pending")
        if (transaction.accountLinkSource.name == "UNLINKED" || (transaction.sourceAccountId == null && transaction.destinationAccountId == null)) return ItemResult(transaction, false, "unlinked")
        val source = accounts.firstOrNull { it.id == transaction.sourceAccountId }
        val destination = accounts.firstOrNull { it.id == transaction.destinationAccountId }
        if ((source != null && !source.isActive) || (destination != null && !destination.isActive)) return ItemResult(transaction, false, "inactive_account")
        if (!accountTypeCompatible(transaction, source, destination)) return ItemResult(transaction, false, "incompatible_account_type")
        return ItemResult(transaction, true, null)
    }

    fun validateBatch(transactions: List<TransactionEntity>, accounts: List<FinancialAccount>): List<ItemResult> = transactions.map { validateOne(it, accounts) }

    /** Posts each valid transaction independently. A failure must not affect the others. */
    suspend fun postValidated(transactions: List<TransactionEntity>, accounts: List<FinancialAccount>, ledger: LedgerRepository): BatchOutcome {
        var confirmed = 0; var failed = 0; var reviewRequired = 0; val issues = mutableListOf<String>()
        for (tx in transactions) {
            val result = validateOne(tx, accounts)
            if (!result.valid) { failed++; issues += "${tx.id}: ${result.reason}"; continue }
            val journalId = tx.linkedJournalEntryId
            if (journalId == null) { failed++; issues += "${tx.id}: no_journal"; continue }
            val validation = ledger.post(journalId)
            if (validation.valid) confirmed++ else { reviewRequired++; issues += "${tx.id}: ${validation.reason}" }
        }
        return BatchOutcome(confirmed = confirmed, failed = failed, reviewRequired = reviewRequired, issues = issues)
    }

    private fun accountTypeCompatible(tx: TransactionEntity, source: FinancialAccount?, destination: FinancialAccount?): Boolean {
        if (source == null && destination == null) return false
        val type = tx.transactionType.name
        return when {
            type.contains("SALARY") -> destination?.accountType == AccountType.BANK_ACCOUNT
            type.contains("CARD_PAYMENT") -> destination?.accountType == AccountType.CREDIT_CARD
            type.contains("INVESTMENT") -> destination?.accountType == AccountType.INVESTMENT_ACCOUNT
            else -> true
        }
    }
}