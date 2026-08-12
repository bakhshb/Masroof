package com.baraa.masroof.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CardDisplayFormatterTest {
    @Test
    fun formatCardLast4_blankOrUnknown_showsDots() {
        assertEquals("····", formatCardLast4(null))
        assertEquals("····", formatCardLast4(""))
        assertEquals("····", formatCardLast4("unknown"))
        assertEquals("····", formatCardLast4("UNKNOWN"))
    }

    @Test
    fun formatCardLast4_normalLast4_passthrough() {
        assertEquals("7271", formatCardLast4("7271"))
    }
}
