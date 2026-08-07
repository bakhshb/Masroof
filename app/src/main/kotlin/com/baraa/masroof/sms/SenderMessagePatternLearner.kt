package com.baraa.masroof.sms

import com.baraa.masroof.transaction.LineBasedFieldParser
import com.baraa.masroof.transaction.ParsedLine
import java.math.BigDecimal
import java.util.Locale

/**
 * Deterministic features extracted from user-selected SMS examples.
 * Never retains the raw body.
 */
data class LearnedSmsFeatures(
    val amountLabels: Set<String>,
    val typeCues: Set<String>,
    val lineLabels: Set<String>,
)

/**
 * Builds structural SMS features from example bodies (INCLUDE) without AI.
 * Amount labels are taken only from lines whose value looks like money and
 * that are not balance/limit labels.
 */
object SenderMessagePatternLearner {

    val TYPE_CUES: Set<String> = setOf(
        "عملية شراء", "شراء", "تحويل", "حوالة", "سحب", "إيداع", "ايداع",
        "سداد", "استرداد", "رسوم", "راتب", "قسط",
        "purchase", "transfer", "withdrawal", "deposit", "payment",
        "refund", "salary", "fee",
    )

    private val MONEY_IN_VALUE = Regex(
        """[-+]?\d{1,3}(?:,\d{3})+(?:\.\d+)?|[-+]?\d+(?:\.\d+)?""",
    )

    fun learnInclude(bodies: List<String>): LearnedSmsFeatures {
        val amountLabels = linkedSetOf<String>()
        val typeCues = linkedSetOf<String>()
        val lineLabels = linkedSetOf<String>()
        for (body in bodies) {
            val lines = LineBasedFieldParser.splitLines(body)
            for (line in lines) {
                val label = line.label.trim()
                if (label.isEmpty()) continue
                lineLabels += label
                if (isBalanceOrLimitLabel(label)) continue
                if (valueLooksLikeMoney(line.value)) {
                    amountLabels += label
                }
            }
            val normalized = BankSmsFilter.normalizeForKeywordSearch(body)
            for (cue in TYPE_CUES) {
                if (cue.lowercase(Locale.ROOT) in normalized) typeCues += cue
            }
        }
        return LearnedSmsFeatures(
            amountLabels = amountLabels,
            typeCues = typeCues,
            lineLabels = lineLabels,
        )
    }

    fun merge(existing: LearnedSmsFeatures, incoming: LearnedSmsFeatures): LearnedSmsFeatures =
        LearnedSmsFeatures(
            amountLabels = existing.amountLabels + incoming.amountLabels,
            typeCues = existing.typeCues + incoming.typeCues,
            lineLabels = existing.lineLabels + incoming.lineLabels,
        )

    fun defaultMinScore(features: LearnedSmsFeatures): Int {
        val rich = features.amountLabels.isNotEmpty() || features.typeCues.isNotEmpty()
        return if (rich) 1 else 2
    }

    fun extractAmountUsingLabels(body: String?, labels: Collection<String>): BigDecimal? {
        if (body.isNullOrBlank() || labels.isEmpty()) return null
        val wanted = labels.map { normalizeLabel(it) }.toSet()
        val lines = LineBasedFieldParser.splitLines(body)
        for (line in lines) {
            if (normalizeLabel(line.label) !in wanted) continue
            if (isBalanceOrLimitLabel(line.label)) continue
            val amount = parsePositiveMoney(line.value) ?: continue
            return amount
        }
        return null
    }

    fun lineLabelsOf(body: String?): Set<String> =
        LineBasedFieldParser.splitLines(body.orEmpty()).map { it.label.trim() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Stable style fingerprint: sorted unique normalized line labels, joined by `|`.
     * Falls back to type cues, then `"empty"`.
     */
    fun structureKeyFromBody(body: String?): String {
        val labels = lineLabelsOf(body).map { normalizeLabel(it) }.filter { it.isNotEmpty() }.toSortedSet()
        if (labels.isNotEmpty()) return labels.joinToString("|")
        val cues = typeCuesIn(body).map { it.lowercase(Locale.ROOT) }.toSortedSet()
        if (cues.isNotEmpty()) return "cues:" + cues.joinToString("|")
        return "empty"
    }

    fun structureKeyFromFeatures(features: LearnedSmsFeatures): String {
        val labels = features.lineLabels.map { normalizeLabel(it) }.filter { it.isNotEmpty() }.toSortedSet()
        if (labels.isNotEmpty()) return labels.joinToString("|")
        val cues = features.typeCues.map { it.lowercase(Locale.ROOT) }.toSortedSet()
        if (cues.isNotEmpty()) return "cues:" + cues.joinToString("|")
        return "empty"
    }

    fun typeCuesIn(body: String?): Set<String> {
        val normalized = BankSmsFilter.normalizeForKeywordSearch(body.orEmpty())
        return TYPE_CUES.filter { it.lowercase(Locale.ROOT) in normalized }.toSet()
    }

    fun amountLabelsPresent(body: String?, labels: Collection<String>): Set<String> {
        if (body.isNullOrBlank() || labels.isEmpty()) return emptySet()
        val wanted = labels.map { normalizeLabel(it) }.toSet()
        return LineBasedFieldParser.splitLines(body)
            .map { it.label.trim() }
            .filter { normalizeLabel(it) in wanted }
            .toSet()
    }

    private fun isBalanceOrLimitLabel(label: String): Boolean {
        if (LineBasedFieldParser.balanceLabelRegex().matches(label.trim())) return true
        val lower = label.lowercase(Locale.ROOT)
        return "رصيد" in label || "balance" in lower || "حد ائتمان" in label || "credit limit" in lower
    }

    private fun valueLooksLikeMoney(value: String): Boolean = parsePositiveMoney(value) != null

    private fun parsePositiveMoney(value: String): BigDecimal? {
        val normalized = value.replace(",", "").trim()
        val match = MONEY_IN_VALUE.find(normalized) ?: return null
        return runCatching { BigDecimal(match.value) }.getOrNull()?.takeIf { it.signum() > 0 }
    }

    private fun normalizeLabel(label: String): String =
        java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .trim()
}
