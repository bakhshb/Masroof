package com.baraa.masroof.application.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleTest {
    @Test
    fun languageCode_stripsRegion() {
        assertEquals("en", AppLocale.languageCode("en"))
        assertEquals("en", AppLocale.languageCode("en-US"))
        assertEquals("ar", AppLocale.languageCode("ar"))
        assertEquals("ar", AppLocale.languageCode("ar-SA"))
        assertEquals("en", AppLocale.languageCode("en_GB"))
    }

    @Test
    fun isRtl_arabicTrue_englishFalse_includingRegions() {
        assertTrue(AppLocale.isRtl("ar"))
        assertTrue(AppLocale.isRtl("ar-SA"))
        assertFalse(AppLocale.isRtl("en"))
        assertFalse(AppLocale.isRtl("en-US"))
    }

    @Test
    fun isEnglish_acceptsRegionalEnglishTags() {
        assertTrue(AppLocale.isEnglish("en"))
        assertTrue(AppLocale.isEnglish("en-US"))
        assertFalse(AppLocale.isEnglish("ar"))
        assertFalse(AppLocale.isEnglish("ar-EG"))
    }
}
