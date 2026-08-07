package com.baraa.masroof.sms

import com.baraa.masroof.transaction.LineBasedFieldParser
import java.util.Locale

/**
 * Tokenizes variable SMS values so structurally identical messages share one signature.
 * Labels and structural order are preserved; amounts/dates/times/last4 become tokens.
 */
object SmsStructureNormalizer {

    private val TIME = Regex("""\b([01]?\d|2[0-3]):[0-5]\d(:[0-5]\d)?\b""")
    private val DATE = Regex(
        """\b(\d{1,2}[-/.\u060c]\d{1,2}[-/.\u060c]\d{2,4}|\d{4}[-/.\u060c]\d{1,2}[-/.\u060c]\d{1,2})\b""",
    )
    private val MONEY = Regex("""\b\d{1,3}(?:,\d{3})+(?:\.\d+)?|\b\d+\.\d{1,4}\b""")
    private val FOUR_DIGIT = Regex("""(?<!\d)\d{4}(?!\d)""")
    private val LONG_DIGITS = Regex("""\d{5,}""")
    private val CURRENCY = Regex("""\b(SAR|SR|ريال|ر\.س)\b""", RegexOption.IGNORE_CASE)

    fun normalizeValue(raw: String): String {
        var s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
        s = s.map { ch ->
            when (ch) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        }.joinToString("")
        s = TIME.replace(s, "<TIME>")
        s = DATE.replace(s, "<DATE>")
        s = MONEY.replace(s, "<DECIMAL_VALUE>")
        s = CURRENCY.replace(s, "<CURRENCY>")
        s = LONG_DIGITS.replace(s, "<REF_OR_ID>")
        s = FOUR_DIGIT.replace(s, "<FOUR_DIGIT_VALUE>")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Signature from labeled lines: `label=<TOKENIZED_VALUE>` joined in order.
     * Falls back to tokenized full body when no labels.
     */
    fun signatureFromBody(body: String?): String {
        if (body.isNullOrBlank()) return "empty"
        val lines = LineBasedFieldParser.splitLines(body)
        if (lines.isNotEmpty()) {
            val parts = lines.mapNotNull { line ->
                val label = normalizeLabel(line.label)
                if (label.isEmpty()) return@mapNotNull null
                val valueTok = normalizeValue(line.value).ifBlank { "<EMPTY>" }
                // Free-text merchant-like values collapse to VARIABLE when not a known token.
                val value = if (looksLikeFreeText(valueTok)) "<VARIABLE_TEXT>" else valueTok
                "$label=$value"
            }
            if (parts.isNotEmpty()) return parts.joinToString("|")
        }
        return "body:" + normalizeValue(body).take(200)
    }

    fun friendlyNameHint(body: String?): String {
        val cues = listOf(
            "شراء عبر نقاط البيع", "شراء عبر الانترنت", "شراء", "تحويل", "حوالة",
            "سحب", "إيداع", "ايداع", "سداد", "رسوم", "رمز التحقق", "OTP",
        )
        val normalized = BankSmsFilter.normalizeForKeywordSearch(body.orEmpty())
        for (cue in cues) {
            if (cue.lowercase(Locale.ROOT) in normalized) return cue
        }
        val firstLabel = LineBasedFieldParser.splitLines(body.orEmpty()).firstOrNull()?.label?.trim()
        return firstLabel?.takeIf { it.isNotBlank() } ?: "نمط رسالة"
    }

    fun looksLikeOtpOrMarketing(body: String?): Boolean {
        if (BankSmsFilter.isOtpOrAuthenticationMessage(body)) return true
        val n = BankSmsFilter.normalizeForKeywordSearch(body.orEmpty())
        return listOf("عرض", "خصم حصري", "اشترك", "promotion", "unsubscribe", "إعلان")
            .any { it in n }
    }

    private fun looksLikeFreeText(tokenized: String): Boolean {
        if (tokenized.startsWith("<")) return false
        if (tokenized.any { it.isDigit() }) return false
        // Prefer: free text of any non-token content collapses.
        return tokenized.length >= 1
    }

    private fun normalizeLabel(label: String): String =
        java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
}
