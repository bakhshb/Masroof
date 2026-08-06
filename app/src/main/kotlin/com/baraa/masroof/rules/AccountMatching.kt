package com.baraa.masroof.rules

import com.baraa.masroof.data.db.FinancialAccount
import java.text.Normalizer

/**
 * Pure-JVM helpers for matching bank-SMS text against the user's owned
 * financial accounts. Conservative on purpose: an ambiguous match returns
 * `null` so the rule engine falls through to PENDING_REVIEW rather than
 * misclassify.
 *
 * Supports:
 *  - institution name match (case-insensitive)
 *  - display name match (case-insensitive)
 *  - sender alias match
 *  - 4-digit number match against `account.lastFourDigits` (Arabic + English
 *    digits normalized to ASCII)
 *  - common label words: "IBAN", "حساب", "card", "بطاقة", "wallet",
 *    "محفظة", "beneficiary", "المستفيد"
 *
 * The 4-digit number match is deliberately permissive about where the
 * number appears: after the keyword ("حساب ****1234"), with the word
 * ("card ending 1234"), or with no keyword at all. The caller is
 * responsible for excluding one account as the source when matching a
 * destination.
 */
object AccountMatching {

    private const val ACCOUNT_LABEL_REGEX =
        """(iban|حساب|card|بطاقة|wallet|محفظة|beneficiary|المستفيد|from\s+card|إلى\s+بطاقة|من\s+بطاقة)"""

    // Match "****1234", "card 1234", "ending 1234", "1234" after a label.
    private val LAST_FOUR_REGEX = Regex(
        """(?i)(?:$ACCOUNT_LABEL_REGEX[^0-9]{0,15}|ending\s+in\s+)?(\d{4})\b"""
    )

    // Generic "any 4 digits" matcher (used for cross-checking).
    private val ANY_FOUR_DIGITS_REGEX = Regex("""\b(\d{4})\b""")

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
     * Match the SMS sender (or any text) against an account's
     * [FinancialAccount.senderAliases]. Returns the matching account or null.
     */
    fun matchBySender(sender: String?, accounts: List<FinancialAccount>): FinancialAccount? {
        if (sender.isNullOrBlank()) return null
        val norm = normalizeDigits(sender.trim()).lowercase()
        return accounts.firstOrNull { acc ->
            acc.senderAliases.any { alias ->
                normalizeDigits(alias.trim()).lowercase() == norm
            }
        }
    }

    /**
     * Match an account by 4-digit number (with optional account label).
     * Looks at `body` and `merchant`. Returns the account whose
     * `lastFourDigits` matches the first 4-digit number in the text, or
     * null if no 4-digit number is present or no account has that
     * lastFour.
     */

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
        return when (matches.size) {
            1 -> matches.first()
            else -> null  // 0 or >1 → ambiguous
        }
    }

    /**
     * Combined match: try last-4 first (most specific), then sender alias,
     * then name. Returns null on ambiguous result.
     */
    fun match(
        sender: String?,
        body: String?,
        merchant: String?,
        accounts: List<FinancialAccount>,
    ): FinancialAccount? {
        // 1. Sender alias — most reliable.
        matchBySender(sender, accounts)?.let { return it }
        // 2. Name (institution / display) — less specific.
        return matchByName(body, merchant, accounts)
    }
}
