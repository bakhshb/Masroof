package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.LineBasedFieldParser
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class CanonicalSmsFields(
    val values: Map<PatternCanonicalField, String>,
    val amount: BigDecimal?,
    val currency: Currency?,
    val date: LocalDate?,
    val time: LocalTime?,
) {
    fun placeholderValues(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        values.forEach { (field, value) ->
            out[TemplateResolutionService.defaultPlaceholder(field)] = value
            out[field.name] = value
        }
        amount?.let {
            out["AMOUNT"] = it.toPlainString()
            out["TRANSACTION_AMOUNT"] = it.toPlainString()
        }
        currency?.let { out["CURRENCY"] = it.name }
        date?.let { out["DATE"] = it.toString() }
        time?.let { out["TIME"] = it.toString() }
        return out
    }
}

/** Strict raw-SMS extractor used after semantic matching. */
object CanonicalSmsFieldExtractor {
    fun extract(body: String?): CanonicalSmsFields {
        val lines = LineBasedFieldParser.splitLines(body.orEmpty())
        val values = linkedMapOf<PatternCanonicalField, String>()
        var amount: BigDecimal? = null
        var currency: Currency? = null
        var date: LocalDate? = null
        var time: LocalTime? = null

        for (line in lines) {
            val fields = CanonicalPatternFieldClassifier.classify(line.label)
            for (field in fields) {
                when (field) {
                    PatternCanonicalField.TRANSACTION_AMOUNT -> {
                        val exact = Regex("^${Regex.escape(line.label)}$", RegexOption.IGNORE_CASE)
                        val parsed =
                            LineBasedFieldParser.parseLabeledMoneyField(listOf(line), listOf(exact))
                                ?: LineBasedFieldParser.parseLeadingMoney(line.value)?.first
                        if (parsed != null) {
                            amount = amount ?: parsed
                            values.putIfAbsent(field, parsed.toPlainString())
                            currency = currency ?: parseCurrency(line.value)
                        }
                    }
                    PatternCanonicalField.AVAILABLE_BALANCE,
                    PatternCanonicalField.CARD_AMOUNT_DUE,
                    -> {
                        LineBasedFieldParser.parseMoneyValue(line.value)
                            ?.let { values.putIfAbsent(field, it.toPlainString()) }
                    }
                    PatternCanonicalField.ACCOUNT_LAST4,
                    PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
                    PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
                    PatternCanonicalField.CREDIT_CARD_LAST4,
                    PatternCanonicalField.DEBIT_CARD_LAST4,
                    PatternCanonicalField.IBAN_LAST4,
                    PatternCanonicalField.SOURCE_IBAN_LAST4,
                    PatternCanonicalField.DESTINATION_IBAN_LAST4,
                    PatternCanonicalField.WALLET_LAST4,
                    -> LineBasedFieldParser.lastFourFromValue(line.value)?.let {
                        values.putIfAbsent(field, it)
                    }
                    PatternCanonicalField.TRANSACTION_DATE,
                    PatternCanonicalField.TRANSACTION_TIME,
                    -> {
                        val parsed = LineBasedFieldParser.parseDateTimeField(listOf(line))
                        parsed.first?.let {
                            date = date ?: it
                            values.putIfAbsent(PatternCanonicalField.TRANSACTION_DATE, it.toString())
                        }
                        parsed.second?.let {
                            time = time ?: it
                            values.putIfAbsent(PatternCanonicalField.TRANSACTION_TIME, it.toString())
                        }
                    }
                    PatternCanonicalField.CURRENCY -> {
                        currency = currency ?: parseCurrency(line.value)
                        currency?.let { values.putIfAbsent(field, it.name) }
                    }
                    else -> if (line.value.isNotBlank()) values.putIfAbsent(field, line.value.trim())
                }
            }
            // Ambiguous bare "إلى"/"الى" (folds to "الي"): the label alone cannot
            // tell account from beneficiary, so the classifier keeps it as
            // BENEFICIARY (preserving a generalizing template/signature). When
            // the value is a 4-digit last4 it is the destination account; extract
            // it here (value-aware, like the rest of this extractor).
            if (normalizedLabelIs(line.label, "الي")) {
                LineBasedFieldParser.lastFourFromValue(line.value)?.let {
                    values.putIfAbsent(PatternCanonicalField.DESTINATION_ACCOUNT_LAST4, it)
                }
            }
        }
        if (amount == null) {
            amount = LineBasedFieldParser.parseTransactionAmount(lines)
            amount?.let {
                values.putIfAbsent(PatternCanonicalField.TRANSACTION_AMOUNT, it.toPlainString())
            }
        }
        return CanonicalSmsFields(values, amount, currency, date, time)
    }

    private fun normalizedLabelIs(rawLabel: String, expected: String): Boolean =
        CanonicalMessageNormalizer.normalizeLabel(rawLabel)
            .replace(Regex("""[\p{P}\p{S}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim() == expected

    private fun parseCurrency(raw: String): Currency? {
        val n = raw.uppercase(Locale.ROOT)
        return when {
            "SAR" in n || Regex("""\bSR\b""").containsMatchIn(n) || "ريال" in raw || "ر.س" in raw ->
                Currency.SAR
            "USD" in n -> Currency.USD
            "EUR" in n -> Currency.EUR
            else -> null
        }
    }
}
