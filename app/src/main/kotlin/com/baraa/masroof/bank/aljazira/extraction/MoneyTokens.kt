package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal

/**
 * Shared money-token parsing for fixture-proven SAR forms:
 * `51.99 SAR`, `SAR 51.99`, `13,258.00 SAR`.
 */
internal object MoneyTokens {
    /** Prefer comma-grouped thousands, else plain integer/decimal (avoids matching only first 3 digits of 17230.03). */
    private val number = """\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+\.\d{1,2}|\d+"""
    val numberRegex = Regex(number)

    /** NUMBER then SAR, or SAR then NUMBER, with optional surrounding spaces. */
    val moneyAfterLabel = Regex(
        """(?::|\s)*\s*(?:(?:($number)\s*sar)|(?:sar\s*($number)))""",
        RegexOption.IGNORE_CASE,
    )

    fun parseNumber(raw: String): Money {
        val normalized = raw.replace(",", "")
        return Money.of(BigDecimal(normalized), Currency.SAR)
    }

    fun parseMoneyFromMatch(match: MatchResult): Money? {
        val raw = match.groupValues[1].ifBlank { match.groupValues.getOrNull(2).orEmpty() }
        if (raw.isBlank()) return null
        return parseNumber(raw)
    }
}
