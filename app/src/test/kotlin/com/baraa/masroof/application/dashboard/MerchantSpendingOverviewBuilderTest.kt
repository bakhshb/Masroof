package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MerchantSpendingOverviewBuilderTest {
    @Test
    fun fourPurchases_doNotQualify() {
        val overview = build(purchases("Cafe", 4))

        assertFalse(overview.hasContent)
    }

    @Test
    fun fivePurchases_qualifyWithExactTransactionIds() {
        val transactions = purchases("Cafe", 5)

        val row = build(transactions).merchants.single()

        assertEquals(5, row.purchaseTransactionCount)
        assertEquals(transactions.map { it.id }.toSet(), row.transactionIds)
        assertEquals(Money.of("50.00", Currency.SAR).amount, row.totalSpent.amount)
    }

    @Test
    fun ranksByTotalSpendingDescending() {
        val overview = build(
            purchases("Lower total", 5, amount = "10") +
                purchases("Higher total", 5, amount = "20"),
        )

        assertEquals(listOf("Higher total", "Lower total"), overview.merchants.map { it.displayName })
    }

    @Test
    fun normalizesOnlySafeTextualDifferencesAndPreservesFrequentDisplayLabel() {
        val transactions = listOf(
            *purchases(" Coffee, Shop ", 3).toTypedArray(),
            *purchases("coffee , shop", 2).toTypedArray(),
        )

        val row = build(transactions).merchants.single()

        assertEquals("coffee,shop", row.merchantKey)
        assertEquals("Coffee, Shop", row.displayName)
    }

    @Test
    fun refund_reducesSpendWithoutIncreasingPurchaseCount() {
        val purchases = purchases("Cafe", 5, amount = "10")
        val refund = transaction(
            id = "refund",
            merchant = "CAFE",
            type = FinancialTransactionType.REFUND,
            amount = "12",
        )

        val row = build(purchases + refund).merchants.single()

        assertEquals(5, row.purchaseTransactionCount)
        assertEquals(Money.of("38.00", Currency.SAR).amount, row.totalSpent.amount)
        assertEquals((purchases.map { it.id } + "refund").toSet(), row.transactionIds)
    }

    @Test
    fun excludesNonMerchantAndNonPurchaseTypes() {
        val transactions = purchases("Cafe", 5) + listOf(
            transaction("transfer", "Cafe", FinancialTransactionType.EXTERNAL_TRANSFER_OUT, "100"),
            transaction("loan", "Cafe", FinancialTransactionType.LOAN_REPAYMENT, "100"),
            transaction("payment", "Cafe", FinancialTransactionType.CREDIT_CARD_PAYMENT, "100"),
            transaction("self", "Cafe", FinancialTransactionType.SELF_TRANSFER, "100"),
            transaction("counterparty", null, FinancialTransactionType.EXPENSE, "100"),
        )

        val row = build(transactions).merchants.single()

        assertEquals(5, row.purchaseTransactionCount)
        assertEquals(Money.of("50.00", Currency.SAR).amount, row.totalSpent.amount)
        assertTrue(row.transactionIds.none { it in setOf("transfer", "loan", "payment", "self", "counterparty") })
    }

    @Test
    fun usesSarEquivalentForForeignMerchantPurchase() {
        val transactions = purchases("Cafe", 4) + transaction(
            id = "usd",
            merchant = "Cafe",
            type = FinancialTransactionType.EXPENSE,
            amount = "10",
            currency = Currency.USD,
        )

        val row = MerchantSpendingOverviewBuilder.build(
            transactions = transactions,
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf("usd" to Money.of("37.50", Currency.SAR)),
        ).merchants.single()

        assertEquals(Money.of("77.50", Currency.SAR).amount, row.totalSpent.amount)
    }

    @Test
    fun dashboardLimitReturnsTopFiveWhileOverviewRetainsAllQualifyingMerchants() {
        val overview = build((1..6).flatMap { index -> purchases("Merchant $index", 5, "$index") })

        assertEquals(6, overview.merchants.size)
        assertEquals(5, overview.topForDashboard().size)
        assertEquals(overview.merchants.take(5), overview.topForDashboard())
    }

    private fun build(transactions: List<FinancialTransaction>): MerchantSpendingOverview =
        MerchantSpendingOverviewBuilder.build(transactions, Currency.SAR, emptyMap())

    private fun purchases(name: String, count: Int, amount: String = "10"): List<FinancialTransaction> =
        (1..count).map { index ->
            transaction("$name-$index", name, FinancialTransactionType.EXPENSE, amount)
        }

    private fun transaction(
        id: String,
        merchant: String?,
        type: FinancialTransactionType,
        amount: String,
        currency: Currency = Currency.SAR,
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, currency),
            occurredAt = Instant.parse("2026-08-01T00:00:00Z"),
            sourceContainerId = null,
            destinationContainerId = null,
            merchant = merchant,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )
}
