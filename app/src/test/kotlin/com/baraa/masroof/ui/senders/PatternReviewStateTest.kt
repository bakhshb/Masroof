package com.baraa.masroof.ui.senders

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.sms.DiscoveredMessagePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternReviewStateTest {

    private fun cluster(
        key: String,
        matchedPatternId: Long? = null,
        otp: Boolean = false,
    ) = DiscoveredMessagePattern(
        signature = "sig-$key",
        friendlyNameHint = key,
        messageCount = 1,
        latestTimestamp = 0L,
        sanitizedSamples = emptyList(),
        suggestedFields = emptyList(),
        looksLikeOtpOrMarketing = otp,
        canonicalKey = "T:$key",
        matchedPatternId = matchedPatternId,
    )

    @Test
    fun everyClusterLandsInExactlyOneSection() {
        val clusters = listOf(
            cluster("new-a"),
            cluster("new-b"),
            cluster("covered", matchedPatternId = 7L),
            cluster("otp", otp = true),
            cluster("covered-otp", matchedPatternId = 8L, otp = true),
        )
        val p = PatternReviewState.partition(clusters)
        assertEquals(
            clusters.size,
            p.needsPattern.size + p.matched.size + p.excluded.size,
        )
        assertEquals(listOf("new-a", "new-b"), p.needsPattern.map { it.friendlyNameHint })
        assertEquals(listOf("covered", "covered-otp"), p.matched.map { it.friendlyNameHint })
        assertEquals(listOf("otp"), p.excluded.map { it.friendlyNameHint })
    }

    @Test
    fun unmatchedGroupsGoToReviewSection() {
        val p = PatternReviewState.partition(listOf(cluster("needs-review")))
        assertEquals(1, p.needsPattern.size)
        assertTrue(p.matched.isEmpty())
        assertTrue(p.excluded.isEmpty())
    }

    @Test
    fun savedPatternMatchesAreNeverOfferedForReCreation() {
        val p = PatternReviewState.partition(listOf(cluster("covered", matchedPatternId = 1L)))
        assertTrue(p.needsPattern.isEmpty())
        assertEquals(1, p.matched.size)
    }

    @Test
    fun otpClustersAreExcludedNotReviewed() {
        val p = PatternReviewState.partition(listOf(cluster("otp", otp = true)))
        assertTrue(p.needsPattern.isEmpty())
        assertEquals(1, p.excluded.size)
    }

    @Test
    fun ignoredLabelOnlyForIgnoredStatus() {
        assertEquals("غير نشط", PatternReviewState.statusLabel(MessagePatternStatus.IGNORED))
        assertNotEquals("غير نشط", PatternReviewState.statusLabel(MessagePatternStatus.UNKNOWN))
        assertNotEquals("غير نشط", PatternReviewState.statusLabel(MessagePatternStatus.APPROVED))
        assertNotEquals("غير نشط", PatternReviewState.statusLabel(MessagePatternStatus.DEPRECATED))
        assertEquals("يحتاج اعتماد", PatternReviewState.statusLabel(MessagePatternStatus.UNKNOWN))
        assertEquals("معتمد", PatternReviewState.statusLabel(MessagePatternStatus.APPROVED))
        assertEquals("مسودة", PatternReviewState.statusLabel(MessagePatternStatus.DEPRECATED))
    }
}
