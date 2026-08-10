package com.baraa.masroof.parsing.normalizer

import com.baraa.masroof.parsing.model.NormalizedSms
import java.text.Normalizer

/**
 * Bank-agnostic SMS text normalizer.
 *
 * Produces deterministic [NormalizedSms] without classifying families, extracting
 * fields, or applying bank-specific label rewrites.
 */
class MessageNormalizer {
    fun normalize(originalBody: String): NormalizedSms {
        val unicodeNormalized = Normalizer.normalize(originalBody, Normalizer.Form.NFC)
        val withLatinDigits = mapArabicIndicDigits(unicodeNormalized)
        val withLineEndings = withLatinDigits
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val withColonVariants = withLineEndings
            .replace('\uFF1A', ':') // fullwidth colon
            .replace('\u061B', ';') // Arabic semicolon kept as semicolon; colons only here
            .replace('\u0703', ':') // Syriac colon-like (if present)
        val normalizedLines = withColonVariants
            .lineSequence()
            .map { line -> collapseInternalSpaces(line.trim()) }
            .toList()
        val normalizedBody = normalizedLines.joinToString("\n")
        val comparisonBody = normalizedBody.lowercase()
        return NormalizedSms(
            originalBody = originalBody,
            normalizedBody = normalizedBody,
            comparisonBody = comparisonBody,
        )
    }

    private fun collapseInternalSpaces(line: String): String =
        line.replace(Regex("[ \\t\\u00A0]+"), " ")

    private fun mapArabicIndicDigits(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            when (val c = chars[i]) {
                in '\u0660'..'\u0669' -> chars[i] = '0' + (c - '\u0660') // Arabic-Indic
                in '\u06F0'..'\u06F9' -> chars[i] = '0' + (c - '\u06F0') // Extended Arabic-Indic
            }
        }
        return String(chars)
    }
}
