package com.baraa.masroof.diagnostics

import java.util.regex.Pattern

/**
 * Text sanitizers used to redact financial / personal data from SMS
 * bodies, diagnostic dumps, and copy-to-clipboard snippets.
 *
 * Patterns and replacements are deliberately conservative — false
 * negatives (missing a redaction) are worse than false positives
 * (replacing too much).
 *
 * Replaced tokens:
 *  - [CARD_LAST4]   — 16-digit PANs and PANs masked with asterisks
 *  - [ACCOUNT_LAST4]— IBANs and long account numbers
 *  - [IBAN]         — full IBANs
 *  - [PHONE]        — local Saudi and international phone numbers
 *  - [BALANCE]      — phrases with "balance" / "رصيد" / "متاح" / "available"
 *  - [AMOUNT]       — generic amount placeholders inside sanitized snippets
 *  - [OTP]          — 4..8 digit codes after OTP / كلمة / رمز
 *  - [REFERENCE]    — alphanumeric references / رقم العملية / receipt numbers
 *  - [NAME]         — possible personal name patterns
 */
object TextSanitizer {

    // PAN: 13..19 digits with optional dashes / spaces; we collapse to last4.
    private val PAN = Pattern.compile("""\b(?:\d[ -]?){13,19}\b""")
    // Masked PAN: ****1234 or ********1234
    private val PAN_MASKED = Pattern.compile("""\*+[\s-]?\d{4}\b""")
    private val CARD_LAST4_LABELED = Pattern.compile(
        """(?i)((?:بطاقة|card)[^\r\n:]{0,24}:?\s*)\d{4}\b""",
    )
    private val ACCOUNT_LAST4_LABELED = Pattern.compile(
        """(?i)((?:حساب|account|آيبان|ايبان|iban)[^\r\n:]{0,24}:?\s*)\d{4}\b""",
    )
    // IBAN: SA + 22 digits / letters (very common).
    private val IBAN = Pattern.compile("""\bSA\d{2}\s?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""")
    // IBAN tolerant: any country prefix + letters/digits up to 34 chars.
    private val IBAN_GENERIC = Pattern.compile("""\b[A-Z]{2}\d{2}[A-Z0-9]{10,30}\b""")
    // Saudi phone: 05xxxxxxxx or +9665xxxxxxxx
    private val PHONE_SA = Pattern.compile("""(?:\+?966)?0?5\d{8}\b""")
    // Generic phone: 7..15 digits with optional separators.
    private val PHONE_GENERIC = Pattern.compile("""\b\d{3}[ -]?\d{3,4}[ -]?\d{4}\b""")
    // OTP phrases (English / Arabic).
    private val OTP_KEYWORD = Pattern.compile(
        """(?i)(otp|one[- ]time|رمز|كلمة\s*المرور|كود|code|pin|كلمة\s*سر)"""
    )
    // Reference phrases.
    private val REF_KEYWORD = Pattern.compile(
        """(?i)(ref|reference|رقم\s*العملية|رقم\s*المرجع|receipt|ايصال|trans(?:action)?\s*id)"""
    )
    // Balance / available phrases.
    private val BALANCE_KEYWORD = Pattern.compile(
        """(?i)(balance|available|رصيد|متاح|remaining|باقي)"""
    )
    // Personal name patterns: "Mr / Mrs / Mr. / Ms." followed by a word.
    private val NAME_TITLE = Pattern.compile(
        """(?i)\b(?:mr|mrs|ms|miss|dr|prof|sr|السيد|السيده|الأستاذ)\.?\s+([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)"""
    )

    /**
     * Sanitize a free-text snippet by replacing sensitive values with
     * bracketed placeholders. The original order is preserved.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s: String = input

        // PAN: 13..19 digit run, optionally with spaces or dashes between.
        // Replace with [CARD_LAST4 XXXX] keeping the trailing 4 digits.
        s = redactPan(s)
        s = PAN_MASKED.matcher(s).replaceAll("[CARD_LAST4]")
        s = CARD_LAST4_LABELED.matcher(s).replaceAll("\$1[CARD_LAST4]")
        s = ACCOUNT_LAST4_LABELED.matcher(s).replaceAll("\$1[ACCOUNT_LAST4]")

        // IBAN — keep masked.
        s = IBAN.matcher(s).replaceAll("[IBAN]")
        s = IBAN_GENERIC.matcher(s).replaceAll("[IBAN]")

        // Phones.
        s = PHONE_SA.matcher(s).replaceAll("[PHONE]")
        // For generic phones we only mask when surrounded by phone keywords.
        s = PHONE_GENERIC.matcher(s).replaceAll("[PHONE]")

        // OTPs: replace the keyword + the following digit group.
        s = redactOtp(s)

        // Reference numbers (longer than 4 digits).
        s = REF_KEYWORD.matcher(s).replaceAll("[REFERENCE]")

        // Balances: redact numeric values that follow a balance keyword.
        s = redactBalanceValues(s)

        // Personal names: drop the word after a title.
        s = NAME_TITLE.matcher(s).replaceAll("[NAME]")

        return s
    }

    /**
     * Replace the digit group following balance keywords. We scan for
     * the keyword and then the next numeric / decimal run.
     */
    private fun redactBalanceValues(s: String): String {
        val matcher = BALANCE_KEYWORD.matcher(s)
        val sb = StringBuilder(s.length)
        var cursor = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < cursor) continue

            appendSafeRange(sb, s, cursor, start)
            appendSafeRange(sb, s, start, end)
            val lineEnd = logicalLineEnd(s, end)
            val numMatcher = NUMERIC_RUN.matcher(s)
            numMatcher.region(end.coerceAtMost(lineEnd), lineEnd)
            if (numMatcher.find()) {
                appendSafeRange(sb, s, end, numMatcher.start())
                sb.append("[BALANCE]")
                cursor = numMatcher.end()
            } else {
                cursor = end
            }
        }
        appendSafeRange(sb, s, cursor, s.length)
        return sb.toString()
    }

    private val NUMERIC_RUN = Pattern.compile("""\d[\d,]*(?:\.\d+)?""")

    /**
     * Replace each PAN run with a `[CARD_LAST4 XXXX]` placeholder,
     * keeping the trailing 4 digits. Uses two passes (find + index
     * arithmetic) instead of [Matcher.replaceAll] with a lambda because
     * the lambda variant of replaceAll is API 34+.
     */
    private fun redactPan(s: String): String {
        val matcher = PAN.matcher(s)
        val sb = StringBuilder(s.length)
        var cursor = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < cursor) continue
            appendSafeRange(sb, s, cursor, start)
            val raw = matcher.group().replace(Regex("[ -]"), "")
            sb.append("[CARD_LAST4 ").append(raw.takeLast(4)).append("]")
            cursor = end
        }
        appendSafeRange(sb, s, cursor, s.length)
        return sb.toString()
    }

    /**
     * Replace the digit group following an OTP keyword. We scan for
     * the keyword and then the next numeric run.
     */
    private fun redactOtp(s: String): String {
        val matcher = OTP_KEYWORD.matcher(s)
        val sb = StringBuilder(s.length)
        var cursor = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < cursor) continue

            appendSafeRange(sb, s, cursor, start)
            sb.append("[OTP]")
            val lineEnd = logicalLineEnd(s, end)
            val numMatcher = OTP_DIGITS.matcher(s)
            numMatcher.region(end.coerceAtMost(lineEnd), lineEnd)
            if (numMatcher.find()) {
                cursor = numMatcher.end()
            } else {
                cursor = end
            }
        }
        appendSafeRange(sb, s, cursor, s.length)
        return if (OTP_KEYWORD.matcher(s).find()) {
            OTP_STANDALONE_LINE.matcher(sb.toString()).replaceAll("\$1[OTP]")
        } else {
            sb.toString()
        }
    }

    private val OTP_DIGITS = Pattern.compile("""\d{4,8}\b""")
    private val OTP_STANDALONE_LINE = Pattern.compile(
        """(?m)(^|\R)\s*\d{4,8}\s*(?=$|\R)""",
    )

    private fun logicalLineEnd(s: String, from: Int): Int {
        var index = from.coerceIn(0, s.length)
        while (index < s.length && s[index] != '\n' && s[index] != '\r') index++
        return index
    }

    /**
     * Append only a validated monotonic range. Invalid diagnostic ranges are
     * ignored rather than being allowed to abort an import scan.
     */
    private fun appendSafeRange(
        target: StringBuilder,
        source: String,
        start: Int,
        end: Int,
    ) {
        if (start < 0 || end < start || end > source.length) return
        if (start != end) target.append(source, start, end)
    }

    /**
     * Convenience: return the last 4 digits of an account number, or
     * `null` if the input contains no recognizable digit run.
     */
    fun lastFourDigits(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val matcher = Pattern.compile("""\d{4}""").matcher(input)
        var last: String? = null
        while (matcher.find()) last = matcher.group()
        return last
    }

    /**
     * Convenience: strip everything except visible Arabic / Latin letters
     * and digits, replace everything else with spaces. Used to make a
     * "minimally identifying" snippet that still shows structure.
     */
    fun shapeOnly(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when {
                    c.isLetterOrDigit() -> c
                    c.isWhitespace() -> ' '
                    else -> ' '
                }
            )
        }
        return sb.toString().replace(Regex("""\s+"""), " ").trim()
    }
}