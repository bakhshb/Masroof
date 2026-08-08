package com.baraa.masroof.data.repository

import com.baraa.masroof.sms.MatchReason
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsImportOrchestratorDiagnosticSafetyTest {
    @Test
    fun physicalDeviceBalanceMessageCannotAbortDiagnosticBatch() {
        val messages = listOf(
            SmsMessage(
                1,
                "BANK",
                "الرصيد المتاح :SAR 17230.03\nإجمالي المبلغ المستحق:2380.88 SAR",
                1L,
                MatchReason.NONE,
            ),
            SmsMessage(2, "BANK", "رسالة غير معروفة", 2L, MatchReason.NONE),
            SmsMessage(3, "BANK", "available balance: SAR 500", 3L, MatchReason.NONE),
        )
        val items = mutableListOf<ScanPreview.PreviewItem>()

        messages.forEach { sms ->
            val sample = safeDiagnosticSample(sms.body)
            assertFalse(sample.orEmpty().contains("17230.03"))
            items += ScanPreview.PreviewItem(
                smsId = sms.id,
                sender = sms.sender,
                amount = null,
                transactionType = TransactionType.OTHER_FINANCIAL,
                proposedAccountId = null,
                proposedAccountName = null,
                isDuplicate = false,
                needsReview = true,
                isBeforeTrackingStart = false,
                date = null,
                disposition = ImportDisposition.UNMATCHED_TEMPLATE,
            )
        }

        val preview = ScanPreview(
            scannedMessages = messages.size,
            unmatchedTemplateMessages = messages.size,
            needsReviewTransactions = messages.size,
            perTransaction = items,
        )
        assertEquals(messages.size, preview.scannedMessages)
        assertEquals(messages.size, preview.perTransaction.size)
        assertTrue(preview.perTransaction.all { it.disposition == ImportDisposition.UNMATCHED_TEMPLATE })
    }

    @Test
    fun sanitizerFailureStoresNoSampleAndNeverFallsBackToRawSms() {
        val raw = "private banking SMS 17230.03"
        val sample = safeDiagnosticSample(raw) { throw IndexOutOfBoundsException("synthetic") }
        assertNull(sample)
        assertFalse(sample.orEmpty().contains(raw))
    }
}
