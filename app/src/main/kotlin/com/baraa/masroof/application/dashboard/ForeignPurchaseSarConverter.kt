package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimates SAR impact for foreign-currency purchases using exchange rate from the SMS body.
 */
object ForeignPurchaseSarConverter {
    fun toSarEquivalent(
        foreignAmount: Money,
        rawSmsBody: String,
        targetCurrency: Currency = Currency.SAR,
        includeInternationalFee: Boolean = true,
    ): Money? {
        if (foreignAmount.currency == targetCurrency) return foreignAmount
        val facts = InternationalPurchaseFactsExtractor.extract(rawSmsBody) ?: return null
        return foreignToSar(
            foreignAmount = foreignAmount,
            exchangeRate = facts.exchangeRate,
            internationalFee = if (includeInternationalFee) facts.internationalFee else null,
            targetCurrency = targetCurrency,
        )
    }

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
