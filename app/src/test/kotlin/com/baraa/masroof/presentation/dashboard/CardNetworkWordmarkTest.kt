package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.domain.model.CardNetwork
import org.junit.Assert.assertEquals
import org.junit.Test

class CardNetworkWordmarkTest {
    @Test
    fun wordmarks_matchPaymentNetworkBrands() {
        assertEquals("VISA", cardNetworkWordmark(CardNetwork.VISA))
        assertEquals("mada", cardNetworkWordmark(CardNetwork.MADA))
        assertEquals("MC", cardNetworkWordmark(CardNetwork.MASTERCARD))
        assertEquals("AMEX", cardNetworkWordmark(CardNetwork.AMEX))
        assertEquals("••", cardNetworkWordmark(CardNetwork.UNKNOWN))
        assertEquals("••", cardNetworkWordmark(null))
    }
}
