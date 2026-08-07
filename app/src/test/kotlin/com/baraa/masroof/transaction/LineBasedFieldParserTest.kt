package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBasedFieldParserTest {
    @Test fun splitsLabeledLines() {
        val body = "شراء\nبمبلغ: 250 ريال\nالبطاقة: 1234\nالرصيد المتاح: 4500 ريال"
        val lines = LineBasedFieldParser.splitLines(body)
        assertEquals(4, lines.size)
        assertEquals("شراء", lines[0].label)
        assertEquals("بمبلغ", lines[1].label)
        assertEquals("250 ريال", lines[1].value)
    }
    @Test fun amountLabelMatches() {
        assertTrue(LineBasedFieldParser.containsAmountLabel("Amount"))
        assertTrue(LineBasedFieldParser.containsAmountLabel("Purchase Amount"))
        assertTrue(LineBasedFieldParser.containsAmountLabel("Transaction Amount"))
        assertTrue(LineBasedFieldParser.containsAmountLabel("of"))
    }

    @Test fun expandsCompactEnglishInlineFields() {
        val body =
            "Online Purchase Apple Pay Credit Card: 8332 at :RIDE APP of : 33.03 SAR on : 2026-07-30 10:01 Available Balance is: 18313.81 SAR Due Amount: 802.62 SAR"
        val lines = LineBasedFieldParser.splitLines(body)
        assertTrue(lines.any { it.label.equals("Credit Card", ignoreCase = true) && it.value.contains("8332") })
        assertTrue(lines.any { it.label.equals("of", ignoreCase = true) && it.value.contains("33.03") })
        assertTrue(lines.any { it.label.equals("at", ignoreCase = true) && it.value.contains("RIDE APP") })
        assertTrue(lines.any { it.label.equals("Due Amount", ignoreCase = true) && it.value.contains("802.62") })
    }
}