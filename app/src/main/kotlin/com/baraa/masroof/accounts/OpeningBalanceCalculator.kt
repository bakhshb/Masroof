package com.baraa.masroof.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Deterministic opening-balance calculator. Pure — no I/O, no Room, no
 * coroutines. Computes:
 *  - [openingAssets]    — sum of active ASSET accounts whose
 *    [includeInNetWorth] is true.
 *  - [openingLiabilities] — sum of active LIABILITY accounts whose
 *    [includeInNetWorth] is true.
 *  - [openingNetWorth]  — `openingAssets - openingLiabilities`.
 *  - [openingLiquidity] — sum of active ASSET accounts whose
 *    [includeInLiquidity] is true.
 *
 * All math is in [BigDecimal]. Per-currency display rounding is
 * applied at the end (sums are kept at full precision throughout the
 * running total and only rounded when the final result is returned).
 *
 * Inactive accounts (`isActive = false`) are **never** counted.
 *
 * The total is **never** stored as an authoritative database value —
 * callers must recompute totals from the live account list. This file
 * is the single source of truth for the formula.
 */
object OpeningBalanceCalculator {

    /**
     * Per-currency totals. We don't aggregate across currencies — the
     * caller is expected to display each currency separately.
     */
    data class PerCurrency(
        val currency: Currency,
        val assets: BigDecimal,
        val liabilities: BigDecimal,
        val liquidity: BigDecimal,
    ) {
        val netWorth: BigDecimal = assets.subtract(liabilities)
    }

    /**
     * One rollup, possibly with multiple per-currency sub-totals.
     */
    data class Totals(
        val perCurrency: Map<Currency, PerCurrency>,
    ) {
        /** Map of currency → display-friendly total (rounded at 2 decimals). */
        val netWorth: Map<Currency, BigDecimal> =
            perCurrency.mapValues { (_, p) -> p.netWorth.setScale(2, RoundingMode.HALF_UP) }
        val assets: Map<Currency, BigDecimal> =
            perCurrency.mapValues { (_, p) -> p.assets.setScale(2, RoundingMode.HALF_UP) }
        val liabilities: Map<Currency, BigDecimal> =
            perCurrency.mapValues { (_, p) -> p.liabilities.setScale(2, RoundingMode.HALF_UP) }
        val liquidity: Map<Currency, BigDecimal> =
            perCurrency.mapValues { (_, p) -> p.liquidity.setScale(2, RoundingMode.HALF_UP) }
    }

    fun compute(accounts: List<FinancialAccount>): Totals {
        // Accumulate at the source precision, then round the displayed
        // per-currency result once. This keeps BigDecimal deterministic
        // while producing conventional two-decimal money totals.
        data class Accumulator(
            var assets: BigDecimal = BigDecimal.ZERO,
            var liabilities: BigDecimal = BigDecimal.ZERO,
            var liquidity: BigDecimal = BigDecimal.ZERO,
        )
        val accumulators = HashMap<Currency, Accumulator>()
        accounts.filter { it.isActive }.forEach { account ->
            val accumulator = accumulators.getOrPut(account.currency) { Accumulator() }
            val isAsset = account.accountNature == AccountNature.ASSET
            if (account.includeInNetWorth) {
                if (isAsset) accumulator.assets = accumulator.assets.add(account.openingBalance)
                else accumulator.liabilities = accumulator.liabilities.add(account.openingBalance)
            }
            if (isAsset && account.includeInLiquidity) {
                accumulator.liquidity = accumulator.liquidity.add(account.openingBalance)
            }
        }
        val perCurrency = accumulators.mapValues { (currency, values) ->
            PerCurrency(
                currency = currency,
                assets = values.assets.setScale(2, RoundingMode.HALF_UP),
                liabilities = values.liabilities.setScale(2, RoundingMode.HALF_UP),
                liquidity = values.liquidity.setScale(2, RoundingMode.HALF_UP),
            )
        }
        return Totals(perCurrency)
    }
}
