package com.baraa.masroof.ui.senders

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.sms.DiscoveredMessagePattern

/**
 * Pure state mapping for the Bank Messages screen.
 *
 * Every discovered message group is in exactly one state:
 *  1. matched — covered by a saved pattern
 *  2. unmatched — new type, needs a pattern
 *  3. user-ignored — saved rows the user explicitly ignored
 *  4. excluded — OTP/marketing, kept out of transaction patterns
 */
object PatternReviewState {

    data class DiscoveredPartition(
        /** Unmatched, non-OTP clusters: offer "إنشاء نمط". */
        val needsPattern: List<DiscoveredMessagePattern>,
        /** Clusters already covered by a saved pattern: informational only. */
        val matched: List<DiscoveredMessagePattern>,
        /** OTP/marketing clusters: excluded, never mixed into patterns. */
        val excluded: List<DiscoveredMessagePattern>,
    )

    fun partition(clusters: List<DiscoveredMessagePattern>): DiscoveredPartition {
        val matched = clusters.filter { it.matchedPatternId != null }
        val remaining = clusters.filter { it.matchedPatternId == null }
        val excluded = remaining.filter { it.looksLikeOtpOrMarketing || it.looksLikeNonFinancial }
        return DiscoveredPartition(
            needsPattern = remaining.filter { !it.looksLikeOtpOrMarketing && !it.looksLikeNonFinancial },
            matched = matched,
            excluded = excluded,
        )
    }

    /**
     * "متجاهل" appears only for rows the user explicitly ignored
     * (userConfirmed IGNORED). UNKNOWN reads as needs-review, never ignored.
     */
    fun statusLabel(status: MessagePatternStatus): String =
        com.baraa.masroof.data.repository.TemplateStatusLabels.statusAr(status)
}
