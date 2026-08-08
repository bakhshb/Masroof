package com.baraa.masroof.data.repository

import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ImportSessionConsistencyTest {

    private fun item(id: Long, disposition: ImportDisposition) = ScanPreview.PreviewItem(
        smsId = id,
        sender = "BANK",
        amount = BigDecimal.TEN,
        transactionType = TransactionType.PURCHASE,
        proposedAccountId = if (disposition == ImportDisposition.READY) 1L else null,
        proposedAccountName = null,
        isDuplicate = disposition == ImportDisposition.EXACT_DUPLICATE,
        needsReview = ScanPreview.isReviewDisposition(disposition),
        isBeforeTrackingStart = false,
        date = null,
        disposition = disposition,
    )

    @Test
    fun messageReviewAndPatternApprovalAreDisjoint() {
        val preview = ScanPreview(
            scannedMessages = 5,
            perTransaction = listOf(
                item(1, ImportDisposition.READY),
                item(2, ImportDisposition.READY),
                item(3, ImportDisposition.NEEDS_ACCOUNT),
                item(4, ImportDisposition.UNMATCHED_TEMPLATE),
                item(5, ImportDisposition.AMBIGUOUS_TEMPLATE),
            ),
        )
        assertEquals(2, preview.readyToImport)
        assertEquals(1, preview.messageReviewCount)
        assertEquals(2, preview.patternApprovalCount)
        assertEquals(3, preview.needsReview)
        assertEquals(
            preview.readyToImport + preview.messageReviewCount + preview.patternApprovalCount,
            preview.perTransaction.size,
        )
    }

    @Test
    fun sessionSurvivesStoreReplaceAndClear() {
        val store = ImportSessionStore()
        val preview = ScanPreview(
            scannedMessages = 3,
            perTransaction = listOf(
                item(1, ImportDisposition.READY),
                item(2, ImportDisposition.NEEDS_ACCOUNT),
                item(3, ImportDisposition.UNMATCHED_TEMPLATE),
            ),
        )
        store.replace(
            ImportSession(
                preview = preview.copy(candidatePatternCount = 1),
                messages = emptyList(),
                trackingStartDate = null,
                mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
            ),
        )
        assertEquals(1, store.current()?.needsMessageReview)
        assertEquals(1, store.current()?.needsPatternApproval)
        assertEquals(1, store.current()?.readyToImport)
        store.clear()
        assertEquals(null, store.current())
    }

    @Test
    fun reviewQueueInvariant_messageReviewVisibleInSession() {
        val preview = ScanPreview(
            scannedMessages = 8,
            recognizedTransactions = 8,
            needsReviewTransactions = 8,
            perTransaction = (1L..8L).map { item(it, ImportDisposition.NEEDS_ACCOUNT) },
        )
        assertTrue(preview.messageReviewCount > 0)
        assertEquals(8, preview.messageReviewCount)
        assertEquals(0, preview.readyToImport)
        // Review screen must not claim empty when session has message reviewables.
        val session = ImportSession(
            preview = preview,
            messages = emptyList(),
            trackingStartDate = null,
            mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
        )
        assertFalse(session.needsMessageReview == 0)
    }

    @Test
    fun patternGatesAreNotMessageReview() {
        assertFalse(ScanPreview.isMessageReviewDisposition(ImportDisposition.UNMATCHED_TEMPLATE))
        assertTrue(ScanPreview.isPatternApprovalDisposition(ImportDisposition.UNMATCHED_TEMPLATE))
        assertTrue(ScanPreview.isMessageReviewDisposition(ImportDisposition.NEEDS_ACCOUNT))
    }

    @Test
    fun templateApprovalFromImportPreservesSessionAndMarksDirty() {
        val store = ImportSessionStore()
        val preview = ScanPreview(
            scannedMessages = 3,
            perTransaction = listOf(
                item(1, ImportDisposition.READY),
                item(2, ImportDisposition.UNMATCHED_TEMPLATE),
                item(3, ImportDisposition.UNMATCHED_TEMPLATE),
            ),
        )
        store.replace(
            ImportSession(
                id = "sess-a",
                preview = preview,
                messages = emptyList(),
                trackingStartDate = null,
                mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
            ),
        )
        store.beginTemplateApprovalFromImport()
        assertTrue(store.isReturnToImportActive())
        store.markTemplatesChanged()
        assertTrue(store.templatesDirty.value)
        assertTrue(store.consumeTemplatesDirty())
        assertFalse(store.templatesDirty.value)
        assertEquals("sess-a", store.current()?.id)
        store.clearReturnToImport()
        assertFalse(store.isReturnToImportActive())
        store.clear()
        assertFalse(store.isReturnToImportActive())
        assertEquals(null, store.current())
    }
}
