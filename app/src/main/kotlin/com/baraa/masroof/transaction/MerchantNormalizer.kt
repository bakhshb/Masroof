package com.baraa.masroof.transaction

import java.text.Normalizer
import java.util.Locale

/**
 * Pure-JVM merchant-name normalizer used to build the
 * [com.baraa.masroof.data.db.MerchantMemoryEntity] lookup key.
 *
 * Goals:
 *  - Case-insensitive ("STARBUCKS" and "starbucks" collide).
 *  - Ignore punctuation and extra whitespace ("Starbucks - KSA" → "starbucks ksa").
 *  - Strip terminal store / branch numbers ("CARREFOUR 1234" → "carrefour").
 *  - Strip common city suffixes ("STARBUCKS RIYADH" → "starbucks").
 *  - Strip common payment-processor prefixes ("STC PAY *" → "stc pay").
 *  - Be conservative: do NOT merge different merchants that share a token.
 */
object MerchantNormalizer {

    // Common Saudi / GCC city names that show up as suffixes.
    private val CITY_SUFFIXES = setOf(
        "riyadh", "jeddah", "mecca", "makkah", "medina", "madinah",
        "dammam", "khobar", "dhahran", "tabuk", "abha", "hail",
        "taif", "buraidah", "jubail", "yanbu", "najran", "jizan",
        "hafar", "albahasaudi", "saudi arabia", "ksa", "sa",
    )

    // Common payment-processor prefixes to strip. Each entry is a single
    // token; multi-word processor names are split here so the per-token
    // filter below can match them.
    private val PROCESSOR_PREFIXES = setOf(
        "stc", "pay", "stcpay", "urpay", "ur",
        "mada", "visa", "mastercard", "amex",
        "apple", "google",
        "مدى",
    )

    // Common noise tokens to drop.
    private val NOISE_TOKENS = setOf(
        "pos", "atm", "purchase", "payment", "transfer", "deposit", "withdrawal",
        "card", "كاش", "نقدي", "شراء", "دفع",
    )

    /**
     * Normalize a free-text merchant name to a stable lookup key.
     *
     * The output is always lowercase ASCII (or near-ASCII for digits /
     * Arabic-Indic digits folded to ASCII). The function never throws and
     * never returns an empty result for non-null input.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val lowered = nfkc.lowercase(Locale.ROOT)

        // Replace common separators with spaces, then collapse.
        val cleaned = lowered
            .replace(SEPARATOR_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

        if (cleaned.isEmpty()) return ""

        // Tokenize.
        val tokens = cleaned.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""

        // Drop city / processor / noise tokens and trailing digits.
        val filtered = tokens
            .filterNot { it in CITY_SUFFIXES }
            .filterNot { it in PROCESSOR_PREFIXES }
            .filterNot { it in NOISE_TOKENS }
            .filterNot { it.all(Char::isDigit) }
            .filterNot { it.length < 2 }
        if (filtered.isEmpty()) {
            // After stripping everything, keep the longest original token
            // so we never collapse to an empty key.
            return tokens.maxBy { it.length }
        }
        return filtered.joinToString(" ")
    }

    private val SEPARATOR_REGEX = Regex("[\\p{Punct}&&[^-/]]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
