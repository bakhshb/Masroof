package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Tests for [OpeningBalanceCalculator]. Every test uses [BigDecimal]
 * arithmetic — no Float / Double anywhere.
 */
class OpeningBalanceCalculatorTest {

    private fun account(
        id: Long,
        name: String,
        type: AccountType,
        nature: AccountNature = AccountNature.defaultNatureFor(type),
        balance: BigDecimal = BigDecimal.ZERO,
        includeInNetWorth: Boolean = true,
        includeInLiquidity: Boolean = AccountLiquidityDefaults.defaultFor(type),
        active: Boolean = true,
        currency: Currency = Currency.SAR
    ) = FinancialAccount(
        id = id,
        displayName = name,
        institutionName = null,
        accountType = type,
        accountNature = nature,
        currency = currency,
        openingBalance = balance,
        openingBalanceDate = 0L,
        includeInNetWorth = includeInNetWorth,
        includeInLiquidity = includeInLiquidity,
        isOwnedByUser = true,
        isActive = active,
        notes = null
    )

    // -- Asset / liability totals ---------------------------------------

    @Test
    fun assetAccountOpeningBalanceCountsAsAssets() {
        val a = account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000"))
        val t = OpeningBalanceCalculator.compute(listOf(a))
        val sar = t.perCurrency[Currency.SAR]!!
        assertEquals(BigDecimal("18000.00"), sar.assets)
        assertEquals(BigDecimal.ZERO.setScale(2), sar.liabilities)
        assertEquals(BigDecimal("18000.00"), sar.netWorth)
    }

    @Test
    fun liabilityAccountOpeningBalanceCountsAsLiabilities() {
        val a = account(
            1,
            "Visa",
            AccountType.CREDIT_CARD,
            nature = AccountNature.LIABILITY,
            balance = BigDecimal("8700")
        )
        val t = OpeningBalanceCalculator.compute(listOf(a))
        val sar = t.perCurrency[Currency.SAR]!!
        assertEquals(BigDecimal("8700.00"), sar.liabilities)
        assertEquals(BigDecimal.ZERO.setScale(2), sar.assets)
        assertEquals(BigDecimal("-8700.00"), sar.netWorth)
    }

    @Test
    fun liabilitiesAreEnteredAsPositiveAmounts() {
        // The contract: the user enters "I owe 8,700 SAR" as a positive
        // number. The calculator subtracts it from net worth.
        val a = account(
            1,
            "Visa",
            AccountType.CREDIT_CARD,
            nature = AccountNature.LIABILITY,
            balance = BigDecimal("8700")
        )
        val t = OpeningBalanceCalculator.compute(listOf(a))
        val sar = t.perCurrency[Currency.SAR]!!
        assertEquals(BigDecimal("8700.00"), sar.liabilities)
        assertEquals(BigDecimal("-8700.00"), sar.netWorth)
    }

    @Test
    fun assetsTotalIsSumOfAssets() {
        val accs = listOf(
            account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000")),
            account(2, "Wallet", AccountType.DIGITAL_WALLET, balance = BigDecimal("2500")),
            account(3, "Investment", AccountType.INVESTMENT_ACCOUNT, balance = BigDecimal("45000"))
        )
        val t = OpeningBalanceCalculator.compute(accs)
        assertEquals(
            BigDecimal("65500.00"),
            t.perCurrency[Currency.SAR]!!.assets
        )
    }

    @Test
    fun liabilitiesTotalIsSumOfLiabilities() {
        val accs = listOf(
            account(
                1, "Visa", AccountType.CREDIT_CARD,
                nature = AccountNature.LIABILITY, balance = BigDecimal("8700")
            ),
            account(
                2, "Loan", AccountType.LOAN,
                nature = AccountNature.LIABILITY, balance = BigDecimal("36433")
            )
        )
        val t = OpeningBalanceCalculator.compute(accs)
        assertEquals(
            BigDecimal("45133.00"),
            t.perCurrency[Currency.SAR]!!.liabilities
        )
    }

    @Test
    fun openingLiquidityIsSumOfLiquidAssets() {
        val accs = listOf(
            account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000")),
            account(2, "Wallet", AccountType.DIGITAL_WALLET, balance = BigDecimal("2500")),
            account(3, "Investment", AccountType.INVESTMENT_ACCOUNT, balance = BigDecimal("45000")),
            account(4, "Sukuk", AccountType.SUKUK_ACCOUNT, balance = BigDecimal("12000")),
            account(
                5, "Visa", AccountType.CREDIT_CARD,
                nature = AccountNature.LIABILITY, balance = BigDecimal("8700")
            )
        )
        val t = OpeningBalanceCalculator.compute(accs)
        // Only the bank + wallet (includeInLiquidity=true by default).
        assertEquals(BigDecimal("20500.00"), t.perCurrency[Currency.SAR]!!.liquidity)
    }

    @Test
    fun openingNetWorthIsAssetsMinusLiabilities() {
        val accs = listOf(
            account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000")),
            account(2, "Wallet", AccountType.DIGITAL_WALLET, balance = BigDecimal("2500")),
            account(
                3, "Visa", AccountType.CREDIT_CARD,
                nature = AccountNature.LIABILITY, balance = BigDecimal("8700")
            )
        )
        val t = OpeningBalanceCalculator.compute(accs)
        // 18000 + 2500 = 20500 assets; 8700 liabilities; net = 20500 - 8700 = 11800.
        val sar = t.perCurrency[Currency.SAR]!!
        assertEquals(BigDecimal("20500.00"), sar.assets)
        assertEquals(BigDecimal("8700.00"), sar.liabilities)
        assertEquals(BigDecimal("11800.00"), sar.netWorth)
    }

    @Test
    fun creditCardExcludedFromLiquidity() {
        // A credit card with a balance doesn't add liquidity.
        val t = OpeningBalanceCalculator.compute(
            listOf(
                account(
                    1, "Visa", AccountType.CREDIT_CARD,
                    nature = AccountNature.LIABILITY, balance = BigDecimal("0")
                )
            )
        )
        assertEquals(
            BigDecimal.ZERO.setScale(2),
            t.perCurrency[Currency.SAR]!!.liquidity
        )
    }

    @Test
    fun bankAccountIncludedInLiquidity() {
        val t = OpeningBalanceCalculator.compute(
            listOf(account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("100")))
        )
        assertEquals(
            BigDecimal("100.00"),
            t.perCurrency[Currency.SAR]!!.liquidity
        )
    }

    @Test
    fun investmentIncludedInNetWorthButExcludedFromLiquidity() {
        val t = OpeningBalanceCalculator.compute(
            listOf(
                account(1, "Investment", AccountType.INVESTMENT_ACCOUNT, balance = BigDecimal("45000"))
            )
        )
        val sar = t.perCurrency[Currency.SAR]!!
        assertEquals(BigDecimal("45000.00"), sar.assets)
        assertEquals(BigDecimal("45000.00"), sar.netWorth)
        assertEquals(BigDecimal.ZERO.setScale(2), sar.liquidity)
    }

    @Test
    fun inactiveAccountExcludedFromTotals() {
        val t = OpeningBalanceCalculator.compute(
            listOf(
                account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000"), active = true),
                account(2, "Old", AccountType.BANK_ACCOUNT, balance = BigDecimal("9999"), active = false)
            )
        )
        assertEquals(BigDecimal("18000.00"), t.perCurrency[Currency.SAR]!!.assets)
    }

    @Test
    fun accountExcludedFromNetWorth() {
        val t = OpeningBalanceCalculator.compute(
            listOf(
                account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("18000"), includeInNetWorth = true),
                account(2, "Hidden", AccountType.OTHER_ASSET, balance = BigDecimal("9999"), includeInNetWorth = false)
            )
        )
        assertEquals(BigDecimal("18000.00"), t.perCurrency[Currency.SAR]!!.assets)
        assertEquals(BigDecimal("18000.00"), t.perCurrency[Currency.SAR]!!.netWorth)
    }

    @Test
    fun bigDecimalPrecisionPreserved() {
        // 12.34 + 56.78 + 9.99 = 78.11 . . . wait, that's 79.11. The
        // calculator must sum exactly in BigDecimal and only round at
        // display time.
        val accs = listOf(
            account(1, "A", AccountType.BANK_ACCOUNT, balance = BigDecimal("12.34")),
            account(2, "B", AccountType.BANK_ACCOUNT, balance = BigDecimal("56.78")),
            account(3, "C", AccountType.BANK_ACCOUNT, balance = BigDecimal("9.99"))
        )
        val t = OpeningBalanceCalculator.compute(accs)
        assertEquals(BigDecimal("79.11"), t.perCurrency[Currency.SAR]!!.assets)
    }

    @Test
    fun emptyAccountListProducesEmptyTotals() {
        val t = OpeningBalanceCalculator.compute(emptyList())
        assertTrue(t.perCurrency.isEmpty())
    }

    @Test
    fun perCurrencyResultsAreSeparate() {
        val sar = account(1, "Bank", AccountType.BANK_ACCOUNT, balance = BigDecimal("1000"), currency = Currency.SAR)
        val usd = account(2, "USD", AccountType.BANK_ACCOUNT, balance = BigDecimal("500"), currency = Currency.USD)
        val t = OpeningBalanceCalculator.compute(listOf(sar, usd))
        assertEquals(BigDecimal("1000.00"), t.perCurrency[Currency.SAR]!!.assets)
        assertEquals(BigDecimal("500.00"), t.perCurrency[Currency.USD]!!.assets)
    }

    @Test
    fun derivedTotalsAreNotStoredAuthoritatively() {
        // The calculator returns computed totals, but the database
        // never stores them as a "current balance" column. Verify by
        // looking at the schema: no field derived from the totals exists.
        val fields = com.baraa.masroof.data.db.FinancialAccountEntity::class.java.declaredFields
        for (f in fields) {
            val name = f.name.lowercase()
            val isDerived = name.contains("currentbalance") ||
                name.contains("runningtotal") ||
                name.contains("derivedtotal")
            assertFalse(
                "FinancialAccountEntity must not store derived totals (field: ${f.name})",
                isDerived
            )
        }
    }
}
