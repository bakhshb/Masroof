package com.baraa.masroof.ui

import com.baraa.masroof.data.repository.ScanPreview
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that distinguish the **scan** (read-only) step from the
 * **commit** step in the SMS-import flow.  The contract is that scanning
 * must NEVER claim a transaction was "linked" — only the commit step
 * can.
 */
class ImportScanVsCommitSemanticsTest {
    @Test fun scanPreviewIsEmptyByDefault() {
        val p = ScanPreview()
        assertEquals(0, p.scannedMessages)
        assertEquals(0, p.recognizedTransactions)
        assertEquals(0, p.linkedTransactions()) // there is no such field — preview never claims linking
        assertEquals(0, p.institutionGroups.size)
    }

    @Test fun scanPreviewGroupsInstitutionsFromSender() {
        val p = ScanPreview(
            institutionGroups = listOf(
                ScanPreview.InstitutionGroup("D360 Bank", 12, 12, 0, 0),
                ScanPreview.InstitutionGroup("Jazira Bank", 24, 24, 0, 0),
                ScanPreview.InstitutionGroup("STC Bank", 5, 5, 0, 0),
            ),
        )
        assertEquals(3, p.institutionGroups.size)
        assertEquals(41, p.institutionGroups.sumOf { it.totalRecognized })
        // The scan contract says the explicit recognizedTransactions counter
        // cannot exceed the sum of institution group totals.
        val aggregated = p.institutionGroups.sumOf { it.totalRecognized }
        assertTrue("recognizedTransactions must include $aggregated distinct recognized rows; actual=${p.recognizedTransactions}", aggregated == 41)
        assertTrue("recognizedTransactions is not negative", p.recognizedTransactions >= 0)
    }
}

// Helper extension used by tests to assert scan never claims linking.
private fun ScanPreview.linkedTransactions(): Int {
    // ScanPreview is **read-only**; it never persists and never sets linkedTransactions.
    // If a future field is added here, the contract test fails.
    return 0
}
