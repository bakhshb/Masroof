package com.baraa.masroof.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-data tests for the structured [SmsImportResult]. Every count has
 * a defined meaning; the UI must NEVER claim "linked 41" or "posted 41"
 * unless those counters match actual Room state.
 */
class SmsImportResultTest {
    @Test fun emptyResultMarksNothingAsSuccess() {
        val r = SmsImportResult.Empty
        assertFalse(r.isSuccess)
        assertEquals(0, r.scannedMessages)
        assertEquals(0, r.linkedTransactions)
        assertEquals(0, r.updatedAccountIds.size)
        assertEquals(0, r.postedTransactions)
    }

    @Test fun permissionMissingCarriesMessage() {
        val r = SmsImportResult.permissionMissing("تعذر فحص الرسائل لأن إذن قراءة الرسائل غير ممنوح.")
        assertTrue(r.permissionMissing)
        assertEquals(0, r.scannedMessages)
        assertNotNull(r.permissionMessage)
    }

    @Test fun affectedAccountsDeduplicateViaCaller() {
        val r = SmsImportResult(
            scannedMessages = 100,
            recognizedTransactions = 41,
            importedTransactions = 41,
            linkedTransactions = 41,
            postedTransactions = 41,
            updatedAccountIds = listOf(1L, 2L, 3L, 1L),
            affectedAccounts = emptyList()
        )
        assertEquals(41, r.linkedTransactions)
        assertEquals(3, r.updatedAccountIds.toSet().size)
    }

    @Test fun importedAtIsZeroUntilCommit() {
        val r = SmsImportResult.Empty
        assertEquals(0L, r.importedAt)
    }
}
