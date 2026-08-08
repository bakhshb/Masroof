package com.baraa.masroof.transaction

import java.math.BigDecimal

/**
 * Collects labeled monetary candidates from parsed SMS lines and selects the
 * transaction amount only from [MonetaryRole.TRANSACTION_AMOUNT] candidates.
 */
object MonetaryAmountSelector {

    private val MONEY = Regex(
        """([A-Z]{2,3})?\s*([-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?)\s*([A-Z]{2,3})?""",
    )

    fun collect(lines: List<ParsedLine>): List<AmountCandidate> {
        val out = mutableListOf<AmountCandidate>()
        for (line in lines) {
            val role = MonetaryFieldClassifier.classify(line.label)
            if (role == MonetaryRole.UNKNOWN) continue
            val money = parseMoney(line.value) ?: continue
            out += AmountCandidate(
                value = money.first,
                currency = money.second,
                semanticRole = role,
                label = line.label,
                evidence = "label:${role.name}:${line.label}",
                confidence = when (role) {
                    MonetaryRole.TRANSACTION_AMOUNT -> 95
                    MonetaryRole.AVAILABLE_BALANCE, MonetaryRole.TOTAL_DUE,
                    MonetaryRole.OUTSTANDING_BALANCE, MonetaryRole.CREDIT_LIMIT,
                    -> 90
                    else -> 70
                },
            )
        }
        return out
    }

    /**
     * Transaction amount = first positive TRANSACTION_AMOUNT candidate.
     * Never falls back to balances, dues, limits, or "largest/last SAR".
     */
    fun selectTransactionAmount(candidates: List<AmountCandidate>): AmountCandidate? =
        candidates.firstOrNull {
            it.semanticRole == MonetaryRole.TRANSACTION_AMOUNT &&
                it.exclusionReason == null &&
                it.value.signum() > 0
        }

    fun availableBalance(candidates: List<AmountCandidate>): BigDecimal? =
        candidates.firstOrNull { it.semanticRole == MonetaryRole.AVAILABLE_BALANCE }?.value

    fun dueAmount(candidates: List<AmountCandidate>): BigDecimal? =
        candidates.firstOrNull {
            it.semanticRole == MonetaryRole.TOTAL_DUE ||
                it.semanticRole == MonetaryRole.OUTSTANDING_BALANCE
        }?.value

    private fun parseMoney(value: String): Pair<BigDecimal, Currency>? {
        // Preserve currency letter case — normalizeForParsing lowercases and
        // would break [A-Z] currency token matching.
        val trimmed = value.trim()
            .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC) }
            .replace('٫', '.')
            .replace('٬', ',')
            .replace(Regex("[٠١٢٣٤٥٦٧٨٩]")) { m ->
                when (m.value[0]) {
                    '٠' -> "0"; '١' -> "1"; '٢' -> "2"; '٣' -> "3"; '٤' -> "4"
                    '٥' -> "5"; '٦' -> "6"; '٧' -> "7"; '٨' -> "8"; '٩' -> "9"
                    else -> m.value
                }
            }
            .replace(Regex("\\s+"), " ")
            .trim()
        if (trimmed.isEmpty()) return null
        val match = MONEY.find(trimmed) ?: return null
        val prefix = match.groupValues[1]
        val amountText = match.groupValues[2].replace(",", "")
        val suffix = match.groupValues[3]
        val amount = runCatching { BigDecimal(amountText) }.getOrNull() ?: return null
        val code = (prefix.ifEmpty { suffix }).uppercase(java.util.Locale.ROOT)
        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        val currency = when (code) {
            "SAR", "SR" -> Currency.SAR
            "USD" -> Currency.USD
            "EUR" -> Currency.EUR
            else -> when {
                "ريال" in lower || "ر.س" in lower || "sar" in lower || "sr" in lower -> Currency.SAR
                else -> Currency.UNKNOWN
            }
        }
        return amount to currency
    }
}
