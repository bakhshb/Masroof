package com.baraa.masroof.sms

import java.text.Normalizer

/**
 * Reasons a message is considered a likely bank/financial message.
 *
 * - [KNOWN_SENDER] the sender matched a known financial institution
 * - [KEYWORDS]     the body contains enough financial keywords
 * - [BOTH]         both signals fired
 * - [NONE]         the message is not considered financial
 */
enum class MatchReason { KNOWN_SENDER, KEYWORDS, BOTH, NONE }

/** Result of classifying a single message. */
data class MatchResult(val isMatch: Boolean, val reason: MatchReason)

/**
 * Heuristic classifier for bank / financial SMS messages.
 *
 * Pure JVM code: no Android imports, safe to unit test on the JVM.
 *
 * The classifier is intentionally conservative and easy to extend:
 *  - Add a new financial sender by appending it to [KNOWN_SENDERS_RAW].
 *  - Add a new keyword by appending it to [ARABIC_KEYWORDS] or [ENGLISH_KEYWORDS].
 *  - Tweak the threshold by changing [KEYWORD_THRESHOLD].
 *
 * Matching rules:
 *  - A message is a match if the normalized sender is in [KNOWN_SENDERS] OR
 *    the body contains at least [KEYWORD_THRESHOLD] distinct financial keywords
 *    (Arabic and English counted together).
 *  - OTP-only / advertisement / personal messages rarely accumulate enough
 *    financial keywords to cross the threshold, so they are naturally rejected.
 */
object BankSmsFilter {

    // -- Internals (must be declared above any property whose initializer uses them) ----

    private val SEPARATOR_REGEX = Regex("[\\s\\-_./]+")
    private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")

    /**
     * Raw, human-readable list of known financial senders.
     * Matching is done against the normalized form, so casing and whitespace
     * in these strings are not significant. Add new entries here.
     */
    val KNOWN_SENDERS_RAW: List<String> = listOf(
        "AlRajhi",
        "Alinma",
        "SNB",
        "RiyadBank",
        "BankAlbilad",
        "BSF",
        "SAB",
        "SAIB",
        "AlJazira",
        "meem",
        "D360",
        "STCBank",
        "urpay",
        "mada",
        "Visa",
        "Mastercard",
    )

    /** Normalized set of known financial senders used for matching. */
    val KNOWN_SENDERS: Set<String> = KNOWN_SENDERS_RAW
        .map { normalizeSender(it) }
        .toSet()

    /** Arabic financial keywords. Substring (case-insensitive) matched against the body. */
    val ARABIC_KEYWORDS: Set<String> = setOf(
        "عملية شراء",
        "شراء",
        "تحويل",
        "حوالة",
        "سحب",
        "إيداع",
        "سداد",
        "استرداد",
        "بطاقة",
        "حساب",
        "مبلغ",
        "ريال",
        "ر.س",
        "الرصيد",
        "عملية مرفوضة",
    )

    /** English financial keywords. Substring (case-insensitive) matched against the body. */
    val ENGLISH_KEYWORDS: Set<String> = setOf(
        "purchase",
        "transaction",
        "transfer",
        "withdrawal",
        "deposit",
        "payment",
        "refund",
        "card",
        "account",
        "amount",
        "sar",
        "balance",
        "declined",
    )

    /**
     * Minimum number of distinct financial keywords (Arabic + English combined)
     * required in a body for the body to be considered a financial message
     * when the sender is not a known financial sender.
     */
    const val KEYWORD_THRESHOLD: Int = 2

    // -- Public API -----------------------------------------------------------

    /**
     * Returns true if the message looks like a bank / financial message.
     * This is the simple yes/no API; use [classifyMessage] when you also need
     * the reason for the match.
     */
    fun isLikelyFinancialMessage(sender: String?, body: String?): Boolean =
        classifyMessage(sender, body).isMatch

    /**
     * Classify a message and return both the boolean verdict and the reason.
     * Safe to call with null inputs — returns [MatchResult] with [MatchReason.NONE].
     */
    fun classifyMessage(sender: String?, body: String?): MatchResult {
        val senderKnown = isKnownFinancialSender(sender)
        val bodyMatch = hasFinancialKeywords(body)
        val reason = when {
            senderKnown && bodyMatch -> MatchReason.BOTH
            senderKnown -> MatchReason.KNOWN_SENDER
            bodyMatch -> MatchReason.KEYWORDS
            else -> MatchReason.NONE
        }
        return MatchResult(isMatch = reason != MatchReason.NONE, reason = reason)
    }

    /**
     * True if the (already-normalized or raw) sender matches a known financial
     * sender after normalization.
     */
    fun isKnownFinancialSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        return normalizeSender(sender) in KNOWN_SENDERS
    }

    /**
     * True if the body contains at least [KEYWORD_THRESHOLD] distinct financial
     * keywords (Arabic and English counted together), after Unicode + case
     * normalization.
     */
    fun hasFinancialKeywords(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val normalized = normalizeForKeywordSearch(body)
        val hits = ARABIC_KEYWORDS.count { it in normalized } +
            ENGLISH_KEYWORDS.count { it in normalized }
        return hits >= KEYWORD_THRESHOLD
    }

    // -- Normalization --------------------------------------------------------

    /**
     * Normalize a sender string for matching:
     *  1. Unicode compatibility decomposition (NFKC) — folds full-width / ligatures
     *  2. Trim
     *  3. Lowercase
     *  4. Strip common SMS short-code prefixes ("AD-", "SMS-")
     *  5. Collapse whitespace, hyphens, underscores, dots, and slashes into nothing
     *
     * Examples:
     *  - "Al Rajhi"   -> "alrajhi"
     *  - "al-rajhi"   -> "alrajhi"
     *  - "AD-AlRajhi" -> "alrajhi"
     *  - "VISA "      -> "visa"
     */
    fun normalizeSender(sender: String?): String {
        if (sender.isNullOrBlank()) return ""
        val nfkc = Normalizer.normalize(sender, Normalizer.Form.NFKC)
        val trimmed = nfkc.trim().lowercase()
        val noPrefix = trimmed
            .removePrefix("ad-")
            .removePrefix("sms-")
        return noPrefix.replace(SEPARATOR_REGEX, "")
    }

    /**
     * Normalize a body for keyword searching:
     *  1. Unicode compatibility decomposition (NFKC)
     *  2. Strip combining diacritics (so Arabic harakat don't break substring search)
     *  3. Lowercase
     *
     * The body is NOT collapsed at the separator level — we want to preserve
     * word boundaries for things like "عملية شراء" (two-word keyword).
     */
    fun normalizeForKeywordSearch(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val noDiacritics = nfkc.replace(COMBINING_MARKS_REGEX, "")
        return noDiacritics.lowercase()
    }

    // -- Internals ------------------------------------------------------------
}
