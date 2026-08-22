package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdParser
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.FinancialTransactionType
import java.util.Locale

data class TransactionListFilterState(
    val searchQuery: String = "",
    val types: Set<FinancialTransactionType> = emptySet(),
    val cardLast4s: Set<String> = emptySet(),
    val accountContainerIds: Set<String> = emptySet(),
    val transactionIds: Set<String> = emptySet(),
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
            types.isNotEmpty() ||
            cardLast4s.isNotEmpty() ||
            accountContainerIds.isNotEmpty() ||
            transactionIds.isNotEmpty()
}

data class TransactionListFilterResult(
    val transactions: List<TransactionPreviewUi>,
    val totalAmount: Money?,
)

object TransactionListFilterEngine {
    private val TYPE_DISPLAY_ORDER = listOf(
        FinancialTransactionType.EXPENSE,
        FinancialTransactionType.INCOME,
        FinancialTransactionType.EXTERNAL_TRANSFER_IN,
        FinancialTransactionType.EXTERNAL_TRANSFER_OUT,
        FinancialTransactionType.CREDIT_CARD_PAYMENT,
        FinancialTransactionType.REFUND,
        FinancialTransactionType.CASH_WITHDRAWAL,
        FinancialTransactionType.BILL_PAYMENT,
        FinancialTransactionType.FEE,
        FinancialTransactionType.SELF_TRANSFER,
        FinancialTransactionType.ADJUSTMENT,
        FinancialTransactionType.UNKNOWN,
    )

    fun apply(
        transactions: List<TransactionPreviewUi>,
        filter: TransactionListFilterState,
        primaryCurrency: Currency = Currency.SAR,
        accountInvolvementByTransactionId: Map<String, Set<String>> = emptyMap(),
    ): TransactionListFilterResult {
        val filtered = transactions.filter { tx ->
            matchesTransactionIds(tx, filter.transactionIds) &&
                matchesTypes(tx, filter.types) &&
                matchesCards(tx, filter.cardLast4s) &&
                matchesAccounts(tx, filter.accountContainerIds, accountInvolvementByTransactionId) &&
                matchesSearch(tx, filter.searchQuery)
        }
        return TransactionListFilterResult(
            transactions = filtered,
            totalAmount = sumAmount(filtered, primaryCurrency),
        )
    }

    fun availableTypes(transactions: List<TransactionPreviewUi>): List<FinancialTransactionType> {
        val present = transactions.map { it.type }.toSet()
        return TYPE_DISPLAY_ORDER.filter { it in present }
    }

    fun availableCardLast4s(
        transactions: List<TransactionPreviewUi>,
        ownedCardKeys: Set<String> = emptySet(),
    ): List<String> {
        if (ownedCardKeys.isEmpty()) return emptyList()
        val pool = mutableSetOf<String>()
        transactions.forEach { tx ->
            val keys = tx.cardOwnershipKeys()
            if (keys.isNotEmpty()) {
                keys.filter { it in ownedCardKeys }
                    .mapTo(pool) { it.substringAfter(':') }
            } else {
                tx.cardLast4?.let { last4 ->
                    if (ownedCardKeys.any { it.endsWith(":$last4") }) {
                        pool.add(last4)
                    }
                }
            }
        }
        return pool.sorted()
    }

    fun availableAccountContainerIds(
        transactions: List<TransactionPreviewUi>,
        ownedAccountContainerIds: Set<String> = emptySet(),
        involvementByTransactionId: Map<String, Set<String>> = emptyMap(),
    ): List<String> {
        if (ownedAccountContainerIds.isEmpty()) return emptyList()
        val fromContainers = transactions.flatMap { tx ->
            FinancialContainerIdParser
                .accountContainerIdsFromContainers(tx.sourceContainerId, tx.destinationContainerId)
                .toList()
        }
        val fromInvolvement = involvementByTransactionId.values.flatten()
        val present = (fromContainers + fromInvolvement).toSet()
        return present.intersect(ownedAccountContainerIds).sorted()
    }

    private fun matchesTransactionIds(
        tx: TransactionPreviewUi,
        transactionIds: Set<String>,
    ): Boolean = transactionIds.isEmpty() || tx.id in transactionIds

    private fun matchesTypes(
        tx: TransactionPreviewUi,
        types: Set<FinancialTransactionType>,
    ): Boolean = types.isEmpty() || tx.type in types

    private fun matchesCards(tx: TransactionPreviewUi, cardLast4s: Set<String>): Boolean =
        cardLast4s.isEmpty() || tx.cardLast4 in cardLast4s

    private fun matchesAccounts(
        tx: TransactionPreviewUi,
        accountContainerIds: Set<String>,
        involvementByTransactionId: Map<String, Set<String>> = emptyMap(),
    ): Boolean {
        if (accountContainerIds.isEmpty()) return true
        if (tx.sourceContainerId in accountContainerIds || tx.destinationContainerId in accountContainerIds) {
            return true
        }
        return involvementByTransactionId[tx.id].orEmpty().any { it in accountContainerIds }
    }

    private fun matchesSearch(tx: TransactionPreviewUi, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true

        if (matchesAmount(tx, query)) return true

        val lowered = query.lowercase(Locale.ROOT)
        if (tx.searchText.contains(lowered)) return true
        return tx.title?.contains(query, ignoreCase = true) == true
    }

    private fun matchesAmount(tx: TransactionPreviewUi, query: String): Boolean {
        val normalized = query.replace(",", "").trim()
        if (normalized.isEmpty() || normalized.none { it.isDigit() }) return false

        val amount = tx.amount.amount
        val plain = amount.stripTrailingZeros().toPlainString()
        val fixed = amount.setScale(Money.SCALE).toPlainString()

        if (plain.contains(normalized) || fixed.contains(normalized)) return true

        normalized.toBigDecimalOrNull()?.let { queryAmount ->
            if (amount.compareTo(queryAmount) == 0) return true
            if (plain.startsWith(normalized) || fixed.startsWith(normalized)) return true
        }
        return false
    }

    private fun sumAmount(
        transactions: List<TransactionPreviewUi>,
        currency: Currency,
    ): Money? {
        val matching = transactions.filter { it.amount.currency == currency }
        if (matching.isEmpty()) return null
        return matching.fold(Money.zero(currency)) { acc, tx -> acc + tx.amount }
    }
}

private fun TransactionPreviewUi.cardOwnershipKeys(): List<String> =
    listOfNotNull(sourceContainerId, destinationContainerId).mapNotNull { containerId ->
        val last4 = FinancialContainerIdParser.cardLast4(containerId) ?: return@mapNotNull null
        val bankId = FinancialContainerIdParser.cardBankId(containerId) ?: return@mapNotNull null
        CardOwnershipKey.of(Bank(bankId), last4)
    }
