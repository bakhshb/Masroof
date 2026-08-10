package com.baraa.masroof.parsing.normalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageNormalizerTest {
    private val normalizer = MessageNormalizer()

    @Test
    fun preservesOriginalBodyUnchanged() {
        val original = "شراء  \r\nبطاقة: ٧٢٧١"
        val result = normalizer.normalize(original)
        assertEquals(original, result.originalBody)
        assertNotEquals(original, result.normalizedBody)
    }

    @Test
    fun normalizesLineEndingsAndTrimsLines() {
        val result = normalizer.normalize("  line1  \r\n\tline2")
        assertEquals("line1\nline2", result.normalizedBody)
    }

    @Test
    fun collapsesRepeatedSpacesButKeepsLines() {
        val result = normalizer.normalize("Amount:   51.99   SAR\nCard:  7271")
        assertEquals("Amount: 51.99 SAR\nCard: 7271", result.normalizedBody)
    }

    @Test
    fun mapsArabicIndicDigitsToLatin() {
        val result = normalizer.normalize("بمبلغ: ٥١.٩٩")
        assertEquals("بمبلغ: 51.99", result.normalizedBody)
    }

    @Test
    fun normalizesFullwidthColon() {
        val result = normalizer.normalize("Amount：51.99")
        assertEquals("Amount:51.99", result.normalizedBody)
    }

    @Test
    fun comparisonBodyIsLowercase() {
        val result = normalizer.normalize("Internet Purchase Amount: 10.00 SAR")
        assertEquals(result.normalizedBody.lowercase(), result.comparisonBody)
    }

    @Test
    fun doesNotEraseMeaningfulNumericFields() {
        val body = "بطاقة: 7271\nبمبلغ: 51.99 SAR\nالرصيد المتاح: 17230.03"
        val result = normalizer.normalize(body)
        assertFalse(result.normalizedBody.contains("****"))
        assertEquals(true, result.normalizedBody.contains("7271"))
        assertEquals(true, result.normalizedBody.contains("51.99"))
        assertEquals(true, result.normalizedBody.contains("17230.03"))
    }
}
