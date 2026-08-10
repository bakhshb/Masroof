package com.baraa.masroof.sms.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsBodyHasherTest {
    @Test
    fun sha256_isDeterministicLowercaseHex() {
        val a = SmsBodyHasher.sha256Hex("شراء عبر الانترنت")
        val b = SmsBodyHasher.sha256Hex("شراء عبر الانترنت")
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertEquals(a, a.lowercase())
    }

    @Test
    fun sha256_preservesExactBodyDifferences() {
        assertNotEquals(
            SmsBodyHasher.sha256Hex("body"),
            SmsBodyHasher.sha256Hex("Body"),
        )
        assertNotEquals(
            SmsBodyHasher.sha256Hex("a\nb"),
            SmsBodyHasher.sha256Hex("a\n b"),
        )
    }

    @Test
    fun sha256_knownVector() {
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SmsBodyHasher.sha256Hex("abc"),
        )
    }
}
