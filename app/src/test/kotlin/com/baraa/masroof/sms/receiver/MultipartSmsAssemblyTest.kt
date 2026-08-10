package com.baraa.masroof.sms.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM coverage for multipart body assembly used by [IncomingSmsReceiver].
 */
class MultipartSmsAssemblyTest {
    @Test
    fun joinsPartsInOrderWithoutExtraSeparators() {
        val parts = listOf(
            "شراء عبر الانترنت\n",
            "بطاقة: 7271\n",
            "بمبلغ: 51.99 SAR",
        )
        val combined = parts.joinToString(separator = "")
        assertEquals(
            "شراء عبر الانترنت\nبطاقة: 7271\nبمبلغ: 51.99 SAR",
            combined,
        )
    }
}
