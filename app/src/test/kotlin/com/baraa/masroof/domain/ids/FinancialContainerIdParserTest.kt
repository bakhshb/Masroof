package com.baraa.masroof.domain.ids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialContainerIdParserTest {
    @Test
    fun cardLast4_parsesCardContainerId() {
        assertEquals("7271", FinancialContainerIdParser.cardLast4("card:BANK_ALJAZIRA:7271"))
    }

    @Test
    fun cardLast4_ignoresAccountContainerId() {
        assertNull(FinancialContainerIdParser.cardLast4("account:BANK_ALJAZIRA:3001"))
    }

    @Test
    fun cardLast4FromContainers_prefersSourceThenDestination() {
        assertEquals(
            "7271",
            FinancialContainerIdParser.cardLast4FromContainers(
                sourceContainerId = "card:BANK_ALJAZIRA:7271",
                destinationContainerId = null,
            ),
        )
        assertEquals(
            "8332",
            FinancialContainerIdParser.cardLast4FromContainers(
                sourceContainerId = null,
                destinationContainerId = "card:BANK_ALJAZIRA:8332",
            ),
        )
    }
}
