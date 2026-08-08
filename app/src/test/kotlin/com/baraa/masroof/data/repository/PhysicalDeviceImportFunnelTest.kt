package com.baraa.masroof.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalDeviceImportFunnelTest {
    @Test
    fun observedBatchRemainsNumericallyExplainable() {
        val funnel = ScanFilterFunnel(
            rawSms = 195,
            afterOtpFilter = 163,
            afterSenderFilter = 97,
            templateInput = 97,
            templateMatched = 61,
            unmatchedTemplate = 36,
            ambiguousTemplate = 0,
            extractionFailed = 6,
        )
        assertTrue(funnel.templateInvariantHolds)
        assertEquals(97, 195 - 32 - 66)
        assertEquals(61, 97 - 36)
        assertEquals(55, 61 - 6)
        assertEquals(55, 36 + 13 + 6)
    }

    @Test
    fun unregisteredGroupsAreCompleteAndNeverCarryMessageSamples() {
        val groups = ScanPreview.aggregateUnregisteredSenders(
            mapOf(
                ("SERVICE-A" to ScanPreview.SkipReason.UNREGISTERED_SENDER) to
                    ScanPreview.SkipAccum(40, "must-not-be-exposed", 20L),
                ("SERVICE-B" to ScanPreview.SkipReason.UNREGISTERED_SENDER) to
                    ScanPreview.SkipAccum(26, "must-not-be-exposed", 10L),
                ("BANK" to ScanPreview.SkipReason.NO_AMOUNT) to
                    ScanPreview.SkipAccum(5, "redacted", 30L),
            ),
        )
        assertEquals(66, groups.sumOf { it.messageCount })
        assertEquals(listOf("SERVICE-A", "SERVICE-B"), groups.map { it.senderDisplay })
        assertTrue(groups.all { it.reason == ScanPreview.SkipReason.UNREGISTERED_SENDER })
        groups.forEach { assertNull(it.redactedSample) }
    }
}
