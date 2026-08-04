package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.FinancialTreatment
import java.time.LocalDate

/** Filter selection is captured as an immutable model so it can be saved and compared. */
data class TransactionFilter(
    val query: String = "",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val needsReview: Boolean = false,
    val unlinked: Boolean = false,
    val unclassified: Boolean = false,
    val expenses: Boolean = false,
    val income: Boolean = false,
    val internalTransfers: Boolean = false,
    val investments: Boolean = false,
    val cardPayments: Boolean = false,
    val refunds: Boolean = false,
    val bankFees: Boolean = false,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val postingStatuses: Set<TransactionPostingStatus> = emptySet(),
) {
    val isEmpty: Boolean get() = query.isBlank() && fromDate == null && toDate == null && needsReview.not() && unlinked.not() &&
        unclassified.not() && expenses.not() && income.not() && internalTransfers.not() && investments.not() &&
        cardPayments.not() && refunds.not() && bankFees.not() && accountId == null && categoryId == null && postingStatuses.isEmpty()
}

object TransactionSearchEngine {
    fun search(transactions: List<TransactionEntity>, accounts: List<FinancialAccount>, categoriesById: Map<Long, String>, filter: TransactionFilter): List<TransactionEntity> {
        val needle = filter.query.trim()
        return transactions.filter { tx -> matches(tx, accounts, categoriesById, filter, needle) }
    }

    private fun matches(transaction: TransactionEntity, accounts: List<FinancialAccount>, categoriesById: Map<Long, String>, filter: TransactionFilter, needle: String): Boolean {
        if (filter.fromDate != null && (transaction.transactionDate ?: java.time.Instant.ofEpochMilli(transaction.smsTimestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) < filter.fromDate) return false
        if (filter.toDate != null && (transaction.transactionDate ?: java.time.Instant.ofEpochMilli(transaction.smsTimestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) > filter.toDate) return false
        if (filter.needsReview && transaction.postingStatus != TransactionPostingStatus.NEEDS_REVIEW) return false
        if (filter.unlinked && transaction.accountLinkSource.name == "USER") return false
        if (filter.unclassified && transaction.categoryId != null) return false
        val typeFilters = listOf(
            FinancialTreatment.EXPENSE to filter.expenses, FinancialTreatment.INCOME to filter.income,
            FinancialTreatment.INTERNAL_TRANSFER to filter.internalTransfers, FinancialTreatment.INVESTMENT to filter.investments,
            FinancialTreatment.CREDIT_CARD_PAYMENT to filter.cardPayments, FinancialTreatment.REFUND to filter.refunds,
            FinancialTreatment.BANK_FEE to filter.bankFees,
        ).filter { it.second }.map { it.first }
        if (typeFilters.isNotEmpty() && transaction.financialTreatment !in typeFilters) return false
        if (filter.postingStatuses.isNotEmpty() && transaction.postingStatus !in filter.postingStatuses) return false
        if (filter.accountId != null) {
            val account = accounts.firstOrNull { it.id == filter.accountId }; if (account == null) return false
            if (transaction.sourceAccountId != account.id && transaction.destinationAccountId != account.id) return false
        }
        if (filter.categoryId != null && transaction.categoryId != filter.categoryId) return false
        if (needle.isNotEmpty()) {
            val merchant = transaction.merchantOrBeneficiary?.lowercase().orEmpty()
            val category = categoriesById[transaction.categoryId]?.lowercase().orEmpty()
            val accountName = accounts.firstOrNull { it.id == transaction.sourceAccountId || it.id == transaction.destinationAccountId }?.displayName?.lowercase().orEmpty()
            val description = transaction.transactionType.name.lowercase()
            if (!needle.lowercase().let { merchant.contains(it) || category.contains(it) || accountName.contains(it) || description.contains(it) }) return false
        }
        return true
    }
}