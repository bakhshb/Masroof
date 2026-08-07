package com.baraa.masroof.ui.accounts

import com.baraa.masroof.ledger.HistoricalAccountRelinkService
import org.junit.Assert.assertTrue
import org.junit.Test

class RelinkSummaryFormatTest {
    @Test
    fun zeroResultsExplainsNoPost() {
        val text = formatRelinkSummary(HistoricalAccountRelinkService.Result())
        assertTrue(text.contains("لم يُرحَّل"))
    }

    @Test
    fun postedSummaryIncludesCounts() {
        val text = formatRelinkSummary(
            HistoricalAccountRelinkService.Result(updated = 3, linkedConfirmed = 2, posted = 2, linkedNeedsReview = 1),
        )
        assertTrue(text.contains("مرحّل 2"))
        assertTrue(text.contains("مراجعة 1"))
    }
}
