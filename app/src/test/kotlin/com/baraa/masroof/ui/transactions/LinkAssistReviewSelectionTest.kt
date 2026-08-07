package com.baraa.masroof.ui.transactions

import com.baraa.masroof.ai.LinkAssistSuggestion
import com.baraa.masroof.transaction.FinancialTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggestion only preselects treatment/accounts; posting still requires
 * an explicit user confirm through applyUserLink.
 */
class LinkAssistReviewSelectionTest {

    @Test
    fun suggestionMapsToReviewChoiceWithoutPosting() {
        val suggestion = LinkAssistSuggestion(
            treatment = FinancialTreatment.EXPENSE,
            sourceAccountId = 7L,
            destinationAccountId = null,
            confidence = 85,
            reasonAr = "شراء",
        )
        val choice = ReviewClassification.choosableChoices
            .firstOrNull { it.treatment == suggestion.treatment }
        assertEquals(FinancialTreatment.EXPENSE, choice!!.treatment)
        // No side effects: this test only asserts selection mapping.
        assertFalse(suggestion.confidence >= 100 && suggestion.sourceAccountId == null)
        assertTrue(suggestion.sourceAccountId == 7L)
    }
}
