package com.baraa.masroof.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPreviewSkippedAggregationTest {

    @Test
    fun aggregateSkipped_sortsByCountThenSender_andCapsAtMax() {
        val buckets = (1..12).associate { i ->
            ("S$i" to ScanPreview.SkipReason.NO_AMOUNT) to ScanPreview.SkipAccum(
                count = i,
                redactedSample = "sample $i",
                latestTimestamp = i.toLong(),
            )
        } + mapOf(
            ("STC" to ScanPreview.SkipReason.UNREGISTERED_SENDER) to ScanPreview.SkipAccum(count = 2),
            ("AlRajhi" to ScanPreview.SkipReason.NO_AMOUNT) to ScanPreview.SkipAccum(
                count = 50,
                redactedSample = "Purchase Amount: [AMOUNT] SAR Card: [CARD_LAST4]",
            ),
        )
        val groups = ScanPreview.aggregateSkipped(buckets)
        assertEquals(ScanPreview.MAX_SKIPPED_GROUPS, groups.size)
        assertEquals("AlRajhi", groups[0].senderDisplay)
        assertEquals(50, groups[0].messageCount)
        assertEquals(ScanPreview.SkipReason.NO_AMOUNT, groups[0].reason)
        assertTrue(groups[0].redactedSample!!.contains("Purchase Amount"))
        assertEquals("تعذّر استخراج المبلغ", groups[0].reasonAr)
    }

    @Test
    fun aggregateSkipped_preservesReasonArabicForUnknownPattern() {
        val groups = ScanPreview.aggregateSkipped(
            mapOf(
                ("AlRajhi" to ScanPreview.SkipReason.UNKNOWN_PATTERN) to
                    ScanPreview.SkipAccum(count = 4, redactedSample = "sample"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals("نمط جديد يحتاج مراجعة", groups[0].reasonAr)
    }

    @Test
    fun aggregateSkipped_preservesReasonArabicForUnregistered() {
        val groups = ScanPreview.aggregateSkipped(
            mapOf(
                ("STC" to ScanPreview.SkipReason.UNREGISTERED_SENDER) to
                    ScanPreview.SkipAccum(count = 3, redactedSample = null),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals("مرسل غير مسجل على حساباتك", groups[0].reasonAr)
        assertNull(groups[0].redactedSample)
    }
}
