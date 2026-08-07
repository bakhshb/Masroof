package com.baraa.masroof.sms

import com.baraa.masroof.data.db.SenderMessagePatternEntity
import com.baraa.masroof.data.db.SenderMessagePatternKind

/**
 * Scores a candidate SMS against taught patterns for one sender.
 * Prefers exact structureKey (selected style), then label/cue overlap.
 */
object SenderMessagePatternMatcher {

    data class Score(
        val total: Int,
        val amountLabelHits: Int,
        val typeCueHits: Int,
        val lineLabelHits: Int,
        val hasLearnedAmount: Boolean,
        val structureKeyMatch: Boolean = false,
    ) {
        val matchesInclude: Boolean get() = structureKeyMatch || hasLearnedAmount || total > 0
    }

    fun scoreInclude(body: String?, pattern: SenderMessagePatternEntity): Score {
        require(pattern.kind == SenderMessagePatternKind.INCLUDE_TRANSACTION)
        val bodyKey = SenderMessagePatternLearner.structureKeyFromBody(body)
        val structureMatch = bodyKey == pattern.structureKey && bodyKey != "empty"
        val amountHits = SenderMessagePatternLearner.amountLabelsPresent(body, pattern.amountLabels).size
        val cueHits = SenderMessagePatternLearner.typeCuesIn(body)
            .count { cue -> pattern.typeCues.any { it.equals(cue, ignoreCase = true) } }
        val lineHits = SenderMessagePatternLearner.lineLabelsOf(body)
            .count { label -> pattern.lineLabels.any { it.equals(label, ignoreCase = true) } }
        val learnedAmount = SenderMessagePatternLearner.extractAmountUsingLabels(body, pattern.amountLabels) != null
        val total = amountHits * 3 + cueHits * 2 + (if (lineHits > 0) 1 else 0) +
            (if (structureMatch) 10 else 0)
        return Score(
            total = total,
            amountLabelHits = amountHits,
            typeCueHits = cueHits,
            lineLabelHits = lineHits,
            hasLearnedAmount = learnedAmount,
            structureKeyMatch = structureMatch,
        )
    }

    fun matchesInclude(body: String?, pattern: SenderMessagePatternEntity): Boolean {
        if (!pattern.active || pattern.kind != SenderMessagePatternKind.INCLUDE_TRANSACTION) return false
        val score = scoreInclude(body, pattern)
        if (score.structureKeyMatch) return true
        if (score.hasLearnedAmount) return true
        return score.total >= pattern.minScore.coerceAtLeast(1)
    }

    /**
     * Best matching INCLUDE pattern for [body], or null if none match.
     * Exact structureKey wins; otherwise highest score among soft matches.
     */
    fun bestIncludeMatch(
        body: String?,
        patterns: List<SenderMessagePatternEntity>,
    ): SenderMessagePatternEntity? {
        val active = patterns.filter { it.active && it.kind == SenderMessagePatternKind.INCLUDE_TRANSACTION }
        if (active.isEmpty()) return null
        val bodyKey = SenderMessagePatternLearner.structureKeyFromBody(body)
        val exact = active.filter { it.structureKey == bodyKey && bodyKey != "empty" }
        if (exact.isNotEmpty()) {
            return exact.maxByOrNull { scoreInclude(body, it).total }
        }
        return active
            .map { pattern -> pattern to scoreInclude(body, pattern) }
            .filter { (pattern, score) ->
                score.hasLearnedAmount || score.total >= pattern.minScore.coerceAtLeast(1)
            }
            .maxByOrNull { it.second.total }
            ?.first
    }

    /**
     * IGNORE patterns only apply when the body is a strong OTP/auth challenge
     * for that taught sender (never on disclaimer-only purchase receipts).
     */
    fun matchesIgnore(body: String?, patterns: List<SenderMessagePatternEntity>): Boolean {
        if (patterns.none { it.active && it.kind == SenderMessagePatternKind.IGNORE_AUTH }) return false
        return BankSmsFilter.isOtpOrAuthenticationMessage(body)
    }

    fun anyIncludeMatch(body: String?, patterns: List<SenderMessagePatternEntity>): Boolean =
        bestIncludeMatch(body, patterns) != null
}
