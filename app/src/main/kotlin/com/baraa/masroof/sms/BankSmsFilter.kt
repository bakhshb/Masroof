package com.baraa.masroof.sms

import java.text.Normalizer

/**
 * Reasons a message is considered a likely bank/financial message.
 *
 * - [KNOWN_SENDER] the sender matched a known financial institution
 * - [KEYWORDS]     the body contains enough financial keywords
 * - [BOTH]         both signals fired
 * - [NONE]         the message is not considered financial (incl. OTP / auth)
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
 *  - OTP / purchase-verification / 3-D Secure messages are never a match,
 *    even from a known bank sender (those are not ledger transactions).
 *  - Otherwise a message is a match if the normalized sender is in
 *    [KNOWN_SENDERS] OR the body contains at least [KEYWORD_THRESHOLD]
 *    distinct financial keywords (Arabic and English counted together).
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
     * Challenge / one-time-password SMS patterns.
     *
     * Important: many Saudi **purchase receipts** end with a safety disclaimer
     * like «لا تشارك رمز التحقق» — that alone must NOT mark the SMS as OTP.
     * We only reject when a real code is being issued (cue + digits) or an
     * explicit 3-D Secure / one-time-password challenge phrase is present.
     */
    private val OTP_CODE_PATTERNS: List<Regex> = listOf(
        Regex("""رمز\s*التحقق\s*[:：=]?\s*\d{4,8}"""),
        Regex("""رمز\s*التأكيد\s*[:：=]?\s*\d{4,8}"""),
        Regex("""كود\s*التحقق\s*[:：=]?\s*\d{4,8}"""),
        Regex("""رمز\s*الأمان\s*[:：=]?\s*\d{4,8}"""),
        Regex("""رمزك\s*هو\s*[:：=]?\s*\d{4,8}"""),
        Regex("""الكود\s*الخاص\s*بك\s*[:：=]?\s*\d{4,8}"""),
        Regex("""أ?دخل\s*الرمز\s*[:：=]?\s*\d{4,8}"""),
        Regex("""استخدم\s*الرمز\s*[:：=]?\s*\d{4,8}"""),
        Regex("""\botp\b[\s\-]*(?:code)?\s*[:：=]?\s*\d{4,8}"""),
        Regex("""\byour\s+otp\s+is\s+\d{4,8}"""),
        Regex("""\byour\s+code\s+is\s+\d{4,8}"""),
        Regex("""verification\s+code\s*[:：=]?\s*\d{4,8}"""),
        Regex("""auth(?:entication)?\s+code\s*[:：=]?\s*\d{4,8}"""),
        Regex("""one[\s\-]?time\s+password\s*[:：=]?\s*\d{4,8}"""),
        Regex("""passcode\s*[:：=]?\s*\d{4,8}"""),
    )

    /** Explicit challenge phrases that do not need adjacent digits. */
    private val OTP_CHALLENGE_PHRASES: Set<String> = setOf(
        "one-time password",
        "one time password",
        "onetime password",
        "كلمة المرور لمرة واحدة",
        "كلمة السر لمرة واحدة",
        "كلمة السر الديناميكية",
        "3d secure",
        "3-d secure",
        "للموافقة على العملية أدخل",
        "للتأكيد أدخل الرمز",
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
     * True when the body is a bank OTP / purchase confirmation code / 3-D Secure
     * challenge — not a ledger transaction, even if it mentions an amount.
     *
     * Disclaimers on normal receipts (e.g. «لا تشارك رمز التحقق مع أي شخص»
     * without an issued code) return false.
     */
    fun isOtpOrAuthenticationMessage(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val normalized = normalizeForKeywordSearch(body)
        if (OTP_CODE_PATTERNS.any { it.containsMatchIn(normalized) }) return true
        return OTP_CHALLENGE_PHRASES.any { it in normalized }
    }

    /**
     * Classify a message and return both the boolean verdict and the reason.
     * Safe to call with null inputs — returns [MatchResult] with [MatchReason.NONE].
     */
    fun classifyMessage(sender: String?, body: String?): MatchResult {
        if (isOtpOrAuthenticationMessage(body)) {
            return MatchResult(isMatch = false, reason = MatchReason.NONE)
        }
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
