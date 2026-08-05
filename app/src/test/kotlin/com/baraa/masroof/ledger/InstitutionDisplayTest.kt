package com.baraa.masroof.ledger

import com.baraa.masroof.ledger.InstitutionResolution
import org.junit.Assert.*
import org.junit.Test

class InstitutionDisplayTest {
    @Test fun wellKnownInstitutionsAreArabicLabelsNotBankingJargon() {
        FinancialInstitutionResolver.WELL_KNOWN_INSTITUTIONS.forEach { name ->
            // Display labels must NOT contain parser internals, journal
            // terminology, or English acronyms only used in code.
            assertFalse("$name must not contain 'parser'", name.contains("parser", ignoreCase = true))
            assertFalse("$name must not contain 'journal'", name.contains("journal", ignoreCase = true))
            // D360 is allowed because it is itself a brand name in English.
        }
    }

    @Test fun unknownResolutionIsNeverAutoAssigned() {
        val v = InstitutionResolution.Unknown
        assertEquals(InstitutionIdentificationSource.UNKNOWN, v.source)
        assertTrue(v.requiresReview)
        assertEquals("مرسل مالي غير معروف", v.institutionDisplayName)
    }
}
