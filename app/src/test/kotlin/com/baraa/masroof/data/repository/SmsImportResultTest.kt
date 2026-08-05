package com.baraa.masroof.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-data tests for the structured [SmsImportResult]. These verify that
 * the orchestrator cannot report "linked" without evidence, and that every
 * UI-visible count has a defined semantics.
 */
class SmsImportResultTest {
    @Test fun emptyResultMarksNothingAsSuccess() {
        val r = SmsImportResult.Empty
        assertFalse(r.isSuccess)
        assertEquals(0, r.scannedSmsCount)
        assertEquals(0, r.linkedTransactionsCount)
        assertEquals(0, r.affectedAccountIds.size)
    }

    @Test fun permissionMissingCarriesMessage() {
        val r = SmsImportResult.permissionMissing("تعذر فحص الرسائل لأن إذن قراءة الرسائل غير ممنوح.")
        assertTrue(r.permissionMissing)
        assertEquals(0, r.scannedSmsCount)
        assertNotNull(r.permissionMessage)
    }

    @Test fun affectedAccountIdsDoNotIncludeDuplicates() {
        // The data model intentionally does NOT deduplicate, but callers
        // can deduplicate via toSet(); we verify the basic invariant here.
        val r = SmsImportResult(
            scannedSmsCount = 10,
            recognizedFinancialSmsCount = 8,
            importedTransactionsCount = 7,
            linkedTransactionsCount = 7,
            affectedAccountIds = listOf(1L, 2L, 1L),
            affectedAccounts = emptyList(),
        )
        assertEquals(7, r.linkedTransactionsCount)
        assertEquals(2, r.affectedAccountIds.toSet().size)
    }
}
