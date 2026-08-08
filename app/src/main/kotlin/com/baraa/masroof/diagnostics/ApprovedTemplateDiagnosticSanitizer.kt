package com.baraa.masroof.diagnostics

import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.MessageTypeCueCatalog
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

/**
 * Aggressive sanitizer for exported matcher diagnostics.
 *
 * It preserves labels and placeholder order, never values. Unknown free-text
 * lines become {TEXT_LINE}; even last-four identifiers are removed.
 */
object ApprovedTemplateDiagnosticSanitizer {
    private val PLACEHOLDER = Regex("""\{[A-Z0-9_]+\}""")
    private val DIGITS = Regex("""[\d٠-٩]{2,}""")
    private val LONG_TOKEN = Regex("""\b[A-Za-z0-9][A-Za-z0-9\-_/]{7,}\b""")

    fun sanitizeMessage(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val template = MessageTemplateEngine.buildFromSms(body).templateText
        return template.lineSequence()
            .mapIndexed { index, line -> sanitizeTemplateLine(line, index == 0) }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun sanitizeTemplateLine(line: String?, titleLine: Boolean = false): String {
        if (line.isNullOrBlank()) return ""
        val split = split(line)
        if (split != null) {
            val (label, separator, value) = split
            val safeLabel = sanitizeStatic(label)
            val safeValue = if (PLACEHOLDER.containsMatchIn(value)) {
                sanitizeStaticKeepingPlaceholders(value)
            } else {
                "{VALUE}"
            }
            return "$safeLabel$separator$safeValue".trim()
        }
        val cue = MessageTypeCueCatalog.detectFromFragment(line)
        return when {
            cue != null -> cue.displayNameAr
            titleLine -> "{MESSAGE_TYPE}"
            else -> "{TEXT_LINE}"
        }
    }

    fun sanitizeSender(sender: String?): String {
        if (sender.isNullOrBlank()) return "{UNKNOWN_SENDER}"
        val trimmed = sender.trim()
        return if (trimmed.count { it.isDigit() } >= 4) {
            "{NUMERIC_SENDER}"
        } else {
            sanitizeStatic(trimmed).take(48)
        }
    }

    fun sanitizeDisplayName(displayName: String?, transactionType: String?): String {
        val type = TransactionTypeTaxonomy.parse(transactionType)
            ?: return "{CUSTOM_TEMPLATE_NAME}"
        val canonical = TransactionTypeTaxonomy.labelAr(type)
        return if (displayName == canonical || displayName == type.name) {
            canonical
        } else {
            "$canonical {CUSTOM_NAME_REDACTED}"
        }
    }

    fun sanitizeCanonicalStructure(signature: String?): String {
        if (signature.isNullOrBlank()) return ""
        return signature.split('|').joinToString("|") { part ->
            when {
                part.startsWith("body:", ignoreCase = true) -> "body:{STRUCTURE}"
                '=' in part -> {
                    val label = part.substringBefore('=')
                    val value = part.substringAfter('=')
                    val safeValue = if (
                        value.startsWith("<") && value.endsWith(">") &&
                        value.matches(Regex("""<[A-Z0-9_:]+>"""))
                    ) {
                        value
                    } else {
                        "{VALUE}"
                    }
                    "${sanitizeStatic(label)}=$safeValue"
                }
                else -> sanitizeStatic(part)
            }
        }
    }

    /** Final guard used by tests and exporter before writing any value. */
    fun containsSensitiveValue(text: String): Boolean {
        val compact = text.replace(PLACEHOLDER, "")
        return Regex("""\b(?:SA\d{2}[A-Z0-9]{10,30}|\+?\d{7,}|\d{4,})\b""")
            .containsMatchIn(compact)
    }

    private fun sanitizeStaticKeepingPlaceholders(raw: String): String {
        val protected = mutableListOf<String>()
        var value = PLACEHOLDER.replace(raw) {
            val index = protected.size
            protected += it.value
            "\u0001$index\u0002"
        }
        value = sanitizeStatic(value)
        protected.forEachIndexed { index, placeholder ->
            value = value.replace("\u0001$index\u0002", placeholder)
        }
        return value
    }

    private fun sanitizeStatic(raw: String): String =
        TextSanitizer.sanitize(raw)
            .replace(DIGITS, "{NUMBER}")
            .replace(LONG_TOKEN, "{TOKEN}")
            .replace(Regex("""[ \t\u00A0\u200E\u200F]+"""), " ")
            .trim()

    private fun split(line: String): Triple<String, String, String>? {
        for (separator in listOf("：", ":", "=")) {
            val index = line.indexOf(separator)
            if (index <= 0) continue
            var valueStart = index + separator.length
            while (valueStart < line.length && line[valueStart].isWhitespace()) valueStart++
            return Triple(
                line.substring(0, index),
                line.substring(index, valueStart),
                line.substring(valueStart),
            )
        }
        return null
    }
}
