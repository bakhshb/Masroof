package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimates SAR impact for foreign-currency purchases using persisted parse-time facts.
 */
object ForeignPurchaseSarConverter {
    fun toSarEquivalent(
        foreignAmount: Money,
        exchangeRate: BigDecimal,
        internationalFee: Money? = null,
        targetCurrency: Currency = Currency.SAR,
        includeInternationalFee: Boolean = true,
    ): Money? =
        foreignToSar(
            foreignAmount = foreignAmount,
            exchangeRate = exchangeRate,
            internationalFee = if (includeInternationalFee) internationalFee else null,
            targetCurrency = targetCurrency,
        )

    fun foreignToSar(
        foreignAmount: Money,
        exchangeRate: BigDecimal,
        internationalFee: Money? = null,
        targetCurrency: Currency = Currency.SAR,
    ): Money? {
        if (!foreignAmount.currency.convertsToSar() || targetCurrency != Currency.SAR) return null
        val converted = foreignAmount.amount
            .multiply(exchangeRate)
            .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        val fee = internationalFee?.amount ?: BigDecimal.ZERO.setScale(Money.SCALE)
        val total = converted.add(fee).setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        return Money.of(total, Currency.SAR)
    }
}
