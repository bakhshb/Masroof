package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import java.math.BigDecimal

/**
 * Shared money-token parsing for fixture-proven forms:
 * `51.99 SAR`, `SAR 51.99`, `USD 23.00`, `23.00 USD`.
 */
internal object MoneyTokens {
    /** Prefer comma-grouped thousands, else plain integer/decimal (avoids matching only first 3 digits of 17230.03). */
    private val number = """\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+\.\d{1,2}|\d+"""
    val numberRegex = Regex(number)

    private val currencyCode = """sar|usd"""

    /** NUMBER CURRENCY or CURRENCY NUMBER, with optional label punctuation/spaces. */
    val moneyAfterLabel = Regex(
        """(?::|\s)*\s*(?:(?:($number)\s*($currencyCode))|(?:($currencyCode)\s*($number)))""",
        RegexOption.IGNORE_CASE,
    )

    fun parseNumber(raw: String, currencyCode: String): Money? {
        val currency = when (currencyCode.uppercase()) {
            "SAR" -> Currency.SAR
            "USD" -> Currency.USD
            else -> return null
        }
        val normalized = raw.replace(",", "")
        return Money.of(BigDecimal(normalized), currency)
    }

    fun parseMoneyFromMatch(match: MatchResult): Money? {
        val numberFirst = match.groupValues[1]
        val currencyAfterNumber = match.groupValues[2]
        val currencyBeforeNumber = match.groupValues[3]
        val numberAfterCurrency = match.groupValues[4]

        return when {
            numberFirst.isNotBlank() && currencyAfterNumber.isNotBlank() ->
                parseNumber(numberFirst, currencyAfterNumber)
            numberAfterCurrency.isNotBlank() && currencyBeforeNumber.isNotBlank() ->
                parseNumber(numberAfterCurrency, currencyBeforeNumber)
            else -> null
        }
    }
}
