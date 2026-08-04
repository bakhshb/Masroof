package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBasedFieldParserTest {
    @Test fun splitsLabeledLines() {
        val body = "شراء\nبمبلغ: 250 ريال\nالبطاقة: 1234\nالرصيد المتاح: 4500 ريال"
        val lines = LineBasedFieldParser.splitLines(body)
        assertEquals(3, lines.size)
        assertEquals("بمبلغ", lines[0].label)
        assertEquals("250 ريال", lines[0].value)
    }
    @Test fun amountLabelMatches() {
        assertTrue(LineBasedFieldParser.containsAmountLabel("Amount"))
        assertTrue(LineBasedFieldParser.containsAmountLabel("Purchase Amount"))
        assertTrue(LineBasedFieldParser.containsAmountLabel("Transaction Amount"))
    }
}