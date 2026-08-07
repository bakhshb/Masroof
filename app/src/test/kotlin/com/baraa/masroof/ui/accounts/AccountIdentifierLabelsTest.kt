package com.baraa.masroof.ui.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountIdentifierLabelsTest {
    @Test
    fun formatsMultipleLastFours() {
        assertEquals(
            "•••• 1111 · •••• 2222 · •••• 3333",
            AccountIdentifierLabels.formatLastFours(listOf("1111", "2222", "3333")),
        )
    }

    @Test
    fun dedupesAndIgnoresInvalid() {
        assertEquals(
            "•••• 1234",
            AccountIdentifierLabels.formatLastFours(listOf("1234", "xx", "1234", "12")),
        )
    }

    @Test
    fun emptyReturnsNull() {
        assertNull(AccountIdentifierLabels.formatLastFours(emptyList()))
        assertNull(AccountIdentifierLabels.formatLastFours(listOf("ab")))
    }
}
