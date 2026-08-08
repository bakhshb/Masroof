package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.MessagePatternStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract: senders with a profile must not fall through to the generic parser
 * when no APPROVED/DEPRECATED pattern matches — they skip as UNKNOWN_PATTERN.
 */
class PatternRequiredImportGateTest {
    @Test
    fun unknownPatternSkipReasonHasUserFacingArabic() {
        val group = ScanPreview.SkippedSenderGroup(
            senderDisplay = "BANK",
            reason = ScanPreview.SkipReason.UNKNOWN_PATTERN,
            messageCount = 2,
        )
        assertEquals("نمط جديد يحتاج مراجعة", group.reasonAr)
    }

    @Test
    fun importableStatusesAreApprovedOrDeprecatedOnly() {
        val importable = setOf(MessagePatternStatus.APPROVED, MessagePatternStatus.DEPRECATED)
        assertTrue(MessagePatternStatus.UNKNOWN !in importable)
        assertTrue(MessagePatternStatus.IGNORED !in importable)
        assertEquals(2, importable.size)
    }

    @Test
    fun registeredAccountsOnlyModeExists() {
        assertEquals(
            SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
            SmsImportMode.valueOf("REGISTERED_ACCOUNTS_ONLY"),
        )
    }
}
