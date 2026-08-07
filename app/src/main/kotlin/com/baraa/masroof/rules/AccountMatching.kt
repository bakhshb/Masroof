package com.baraa.masroof.rules

import com.baraa.masroof.data.db.FinancialAccount

/**
 * Pure-JVM helpers for matching bank-SMS text against the user's owned
 * financial accounts. Conservative on purpose: an ambiguous match returns
 * `null` so the rule engine falls through to PENDING_REVIEW rather than
 * misclassify.
 *
 * Supports institution / display name match only. Sender-alias and
 * last-four matching belong in [com.baraa.masroof.ledger.AccountMatcher]
 * / typed [com.baraa.masroof.data.db.AccountIdentifierEntity] rows.
 */
object AccountMatching {

    /**
     * Normalize Arabic-Indic digits ٠-٩ to ASCII 0-9. We also fold
     * Arabic decimal separator ٫ to a regular dot. The input is otherwise
     * left unchanged.
     */
    fun normalizeDigits(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '٠' -> sb.append('0')
                '١' -> sb.append('1')
                '٢' -> sb.append('2')
                '٣' -> sb.append('3')
                '٤' -> sb.append('4')
                '٥' -> sb.append('5')
                '٦' -> sb.append('6')
                '٧' -> sb.append('7')
                '٨' -> sb.append('8')
                '٩' -> sb.append('9')
                '٫' -> sb.append('.')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Match an account by name token (institution or display name) in
     * `body` or `merchant`. Conservative: requires at least one
     * meaningful token (>2 chars) to match. Returns null if multiple
     * accounts match (ambiguous → PENDING_REVIEW).
     */
    fun matchByName(
        body: String?,
        merchant: String?,
        accounts: List<FinancialAccount>,
    ): FinancialAccount? {
        if (body.isNullOrBlank() && merchant.isNullOrBlank()) return null
        val text = normalizeDigits((body.orEmpty() + " " + merchant.orEmpty())).lowercase()
        val tokens = text.split(Regex("""[\s\p{Punct}]+"""))
            .filter { it.length >= 3 }
        if (tokens.isEmpty()) return null
        val matches = accounts.filter { acc ->
            val sources = listOfNotNull(
                acc.displayName.takeIf { it.isNotBlank() },
                acc.institutionName?.takeIf { it.isNotBlank() },
            )
            sources.any { src ->
                val srcTokens = src.lowercase().split(Regex("""[\s\p{Punct}]+"""))
                    .filter { it.length >= 3 }
                srcTokens.isNotEmpty() && srcTokens.all { it in tokens }
            }
        }
        return matches.singleOrNull()
    }

    /**
     * Name-based match only. Returns null on ambiguous result. Never
     * silently picks the first of multiple matches. Sender / last-four
     * evidence is handled exclusively by AccountMatcher.
     */
    fun match(
        sender: String?,
        body: String?,
        merchant: String?,
        accounts: List<FinancialAccount>,
    ): FinancialAccount? {
        // sender is intentionally unused — SenderProfile matching lives in AccountMatcher
        // lives in AccountMatcher / AccountIdentifierRepository.
        return matchByName(body, merchant, accounts)
    }
}
