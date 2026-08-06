package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SenderNormalizerTest {
    @Test fun normalizesCaseSpacesPunctuationAndArabicDigits() {
        assertEquals("snbalert123", SenderNormalizer.normalize("  SNB-ALERT ١٢٣ "))
        assertEquals("snbalert", SenderNormalizer.normalize("snb alert"))
    }

    @Test fun doesNotFuzzilyMergeDifferentSenders() {
        assertNotEquals(SenderNormalizer.normalize("snb"), SenderNormalizer.normalize("alrajhi"))
    }
}
