package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionListFilterEngineTest {
    @Test
    fun noFilter_returnsAllTransactions() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", merchant = "Keeta", card = "7271"),
            preview(type = FinancialTransactionType.INCOME, amount = "5000"),
        )
        val result = TransactionListFilterEngine.apply(txs, TransactionListFilterState())
        assertEquals(2, result.transactions.size)
        assertEquals(Money.of("5100.00", Currency.SAR), result.totalAmount)
    }

    @Test
    fun typeFilter_limitsResults() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", card = "7271"),
            preview(type = FinancialTransactionType.INCOME, amount = "5000"),
        )
        val result = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(type = FinancialTransactionType.EXPENSE),
        )
        assertEquals(1, result.transactions.size)
        assertEquals(FinancialTransactionType.EXPENSE, result.transactions.single().type)
        assertEquals(Money.of("100.00", Currency.SAR), result.totalAmount)
    }

    @Test
    fun cardFilter_limitsResults() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", card = "7271"),
            preview(type = FinancialTransactionType.EXPENSE, amount = "50", card = "3478"),
        )
        val result = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(cardLast4 = "7271"),
        )
        assertEquals(1, result.transactions.size)
        assertEquals("7271", result.transactions.single().cardLast4)
    }

    @Test
    fun typeAndCardFilter_combineWithAnd() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", card = "7271"),
            preview(type = FinancialTransactionType.REFUND, amount = "20", card = "7271"),
            preview(type = FinancialTransactionType.EXPENSE, amount = "50", card = "3478"),
        )
        val result = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(
                type = FinancialTransactionType.EXPENSE,
                cardLast4 = "7271",
            ),
        )
        assertEquals(1, result.transactions.size)
        assertEquals(Money.of("100.00", Currency.SAR), result.totalAmount)
    }

    @Test
    fun searchByMerchantName_matchesCounterpartyToo() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", merchant = "Keeta"),
            preview(type = FinancialTransactionType.EXTERNAL_TRANSFER_IN, amount = "500", counterparty = "نجاه"),
        )
        val result = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(searchQuery = "keeta"),
        )
        assertEquals(1, result.transactions.size)
        assertEquals("Keeta", result.transactions.single().title)
    }

    @Test
    fun searchByAmount_partialAndExact() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "438.50", merchant = "SEC"),
        )
        val partial = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(searchQuery = "438"),
        )
        assertEquals(1, partial.transactions.size)

        val exact = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(searchQuery = "438.5"),
        )
        assertEquals(1, exact.transactions.size)

        val miss = TransactionListFilterEngine.apply(
            txs,
            TransactionListFilterState(searchQuery = "439"),
        )
        assertEquals(0, miss.transactions.size)
    }

    @Test
    fun availableTypes_followsDisplayOrder() {
        val txs = listOf(
            preview(type = FinancialTransactionType.INCOME, amount = "1"),
            preview(type = FinancialTransactionType.EXPENSE, amount = "2"),
            preview(type = FinancialTransactionType.FEE, amount = "3"),
        )
        assertEquals(
            listOf(
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.INCOME,
                FinancialTransactionType.FEE,
            ),
            TransactionListFilterEngine.availableTypes(txs),
        )
    }

    @Test
    fun nonPrimaryCurrency_excludedFromTotal() {
        val txs = listOf(
            preview(type = FinancialTransactionType.EXPENSE, amount = "100", currency = Currency.SAR),
            preview(type = FinancialTransactionType.EXPENSE, amount = "23", currency = Currency.USD),
        )
        assertEquals(
            Money.of("100.00", Currency.SAR),
            TransactionListFilterEngine.apply(txs, TransactionListFilterState()).totalAmount,
        )
    }

    private fun preview(
        type: FinancialTransactionType,
        amount: String,
        merchant: String? = null,
        counterparty: String? = null,
        card: String? = null,
        currency: Currency = Currency.SAR,
    ): TransactionPreviewUi {
        val money = Money.of(amount, currency)
        val title = merchant ?: counterparty
        val searchText = listOfNotNull(merchant, counterparty).joinToString(" ").lowercase()
        return TransactionPreviewUi(
            id = "$type-$amount-$merchant-$card",
            title = title,
            amount = money,
            amountLabel = money.amount.toPlainString(),
            dateLabel = "1 أغسطس",
            type = type,
            typeLabelResHint = type,
            direction = TransactionTypePresentation.direction(type),
            cardLast4 = card,
            searchText = searchText,
        )
    }
}
