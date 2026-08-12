package com.baraa.masroof.bank.aljazira.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DueDateExtractorTest {
    private val extractor = DueDateExtractor()

    @Test
    fun extractsDueDateFromStatementSms() {
        assertEquals(
            LocalDate.parse("2026-09-07"),
            extractor.extractFromText("تاريخ الاستحقاق: 07/09/2026"),
        )
    }

    @Test
    fun missingDueDate_returnsNull() {
        assertNull(extractor.extractFromText("إجمالي المبلغ المستحق: 0.00 SAR"))
    }
}
