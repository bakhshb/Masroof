package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
import com.baraa.masroof.transaction.FinancialTreatment
import java.time.LocalDate
import java.time.ZoneId

enum class TransactionSort {
    NEWEST,
    OLDEST,
    AMOUNT_HIGH_TO_LOW,
    AMOUNT_LOW_TO_HIGH,
}

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
    val sort: TransactionSort = TransactionSort.NEWEST,
) {
    val isEmpty: Boolean get() = query.isBlank() && fromDate == null && toDate == null && needsReview.not() && unlinked.not() &&
        unclassified.not() && expenses.not() && income.not() && internalTransfers.not() && investments.not() &&
        cardPayments.not() && refunds.not() && bankFees.not() && accountId == null && categoryId == null && postingStatuses.isEmpty()
}

object TransactionSearchEngine {
    fun search(transactions: List<TransactionEntity>, accounts: List<FinancialAccount>, categoriesById: Map<Long, String>, filter: TransactionFilter): List<TransactionEntity> {
        val needle = normalizeSearch(filter.query)
        return transactions
            .filter { tx -> matches(tx, accounts, categoriesById, filter, needle) }
            .let { sort(it, filter.sort) }
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
            val accountNames = accounts.filter {
                it.id == transaction.sourceAccountId || it.id == transaction.destinationAccountId
            }.joinToString(" ") { it.displayName }
            val searchable = listOfNotNull(
                transaction.merchantOrBeneficiary,
                com.baraa.masroof.ui.TransactionTypeVisuals.label(transaction.transactionType),
                transaction.transactionType.name,
                transaction.originalSender,
                accountNames,
                categoriesById[transaction.categoryId],
                transaction.accountOrCardLastFourDigits?.let { "••••$it $it" },
                transaction.amount?.stripTrailingZeros()?.toPlainString(),
            ).joinToString(" ")
            if (!normalizeSearch(searchable).contains(needle)) return false
        }
        return true
    }

    private fun sort(
        transactions: List<TransactionEntity>,
        sort: TransactionSort,
    ): List<TransactionEntity> = when (sort) {
        TransactionSort.NEWEST -> transactions.sortedWith(
            compareByDescending<TransactionEntity>(::effectiveFinancialTime)
                .thenByDescending { it.id },
        )
        TransactionSort.OLDEST -> transactions.sortedWith(
            compareBy<TransactionEntity>(::effectiveFinancialTime)
                .thenBy { it.id },
        )
        TransactionSort.AMOUNT_HIGH_TO_LOW -> transactions.sortedWith(
            compareByDescending<TransactionEntity> { it.amount }
                .thenByDescending(::effectiveFinancialTime)
                .thenByDescending { it.id },
        )
        TransactionSort.AMOUNT_LOW_TO_HIGH -> transactions.sortedWith(
            compareBy<TransactionEntity, java.math.BigDecimal?>(nullsLast()) { it.amount }
                .thenByDescending(::effectiveFinancialTime)
                .thenByDescending { it.id },
        )
    }

    internal fun effectiveFinancialTime(transaction: TransactionEntity): Long {
        val date = transaction.transactionDate ?: return transaction.smsTimestamp
        return date.atTime(transaction.transactionTime ?: java.time.LocalTime.MIN)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    internal fun normalizeSearch(value: String): String = value
        .lowercase()
        .replace(Regex("[أإآ]"), "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}