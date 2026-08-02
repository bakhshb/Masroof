package com.baraa.masroof.transaction

import java.text.Normalizer
import java.util.Locale

/**
 * Pure-JVM text normalization for bank SMS bodies.
 *
 * - Unicode compatibility decomposition (NFKC) — folds full-width / ligatures
 * - Convert Arabic-Indic digits ٠-٩ → ASCII 0-9
 * - Convert Arabic decimal separator ٫ → . and thousands separator ٬ → ,
 * - Strip combining diacritics (Arabic harakat, Latin accents) via \p{M}
 * - Collapse all whitespace (incl. \r, \n, \t) into single ASCII spaces
 * - Trim
 *
 * The result is suitable for substring / regex search against canonical
 * Arabic + English keyword lists. It is NOT suitable for display — keep
 * [ParsedTransaction.originalMessage] for that.
 */
object BankTextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Normalize a single piece of text. Returns "" for null or blank input.
     * The output is lowercased so that keyword / amount matching can be
     * case-insensitive without per-call allocations. The original casing is
     * preserved in [ParsedTransaction.originalMessage].
     */
    fun normalizeForParsing(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC)
        s = toAsciiDigits(s)
        s = s.replace(COMBINING_MARKS, "")
        s = s.replace(WHITESPACE, " ")
        return s.trim().lowercase(Locale.ROOT)
    }

    private fun toAsciiDigits(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        for (c in s) {
            out.append(
                when (c) {
                    '٠' -> '0'
                    '١' -> '1'
                    '٢' -> '2'
                    '٣' -> '3'
                    '٤' -> '4'
                    '٥' -> '5'
                    '٦' -> '6'
                    '٧' -> '7'
                    '٨' -> '8'
                    '٩' -> '9'
                    '٫' -> '.' // Arabic decimal separator
                    '٬' -> ',' // Arabic thousands separator
                    else -> c
                }
            )
        }
        return out.toString()
    }
}
