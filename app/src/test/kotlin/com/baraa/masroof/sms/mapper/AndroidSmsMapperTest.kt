package com.baraa.masroof.sms.mapper

import com.baraa.masroof.sms.hash.SmsBodyHasher
import com.baraa.masroof.sms.model.ProviderSmsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AndroidSmsMapperTest {
    @Test
    fun providerId_producesStableAndroidSmsId() {
        val record = ProviderSmsRecord(
            providerMessageId = "42",
            sender = "AlJazira",
            body = "exact body",
            receivedAt = Instant.ofEpochMilli(1_700_000_000_000L),
        )
        val first = AndroidSmsMapper.toRawSms(record)
        val second = AndroidSmsMapper.toRawSms(record)
        assertEquals("android-sms:42", first.id)
        assertEquals(first.id, second.id)
        assertEquals("42", first.deviceMessageId)
        assertEquals("exact body", first.body)
        assertEquals("AlJazira", first.sender)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), first.receivedAt)
        assertEquals(SmsBodyHasher.sha256Hex("exact body"), first.bodyHash)
    }

    @Test
    fun liveWithoutProviderId_usesDeterministicFallbackId() {
        val receivedAt = Instant.ofEpochMilli(1_725_000_000_000L)
        val body = "live body"
        val hash = SmsBodyHasher.sha256Hex(body)
        val raw = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord(
                providerMessageId = null,
                sender = "AlJazira",
                body = body,
                receivedAt = receivedAt,
            ),
        )
        assertEquals("android-sms-live:AlJazira|${receivedAt.toEpochMilli()}|$hash", raw.id)
        assertNull(raw.deviceMessageId)
        assertEquals(hash, raw.bodyHash)
    }

    @Test
    fun sameEvidence_sameIdentity() {
        val record = ProviderSmsRecord("7", "AlJazira", "x", Instant.parse("2026-08-01T00:00:00Z"))
        assertEquals(AndroidSmsMapper.toRawSms(record), AndroidSmsMapper.toRawSms(record))
    }

    @Test
    fun changedBody_changesHash() {
        val t = Instant.parse("2026-08-01T00:00:00Z")
        val a = AndroidSmsMapper.toRawSms(ProviderSmsRecord("1", "AlJazira", "a", t))
        val b = AndroidSmsMapper.toRawSms(ProviderSmsRecord("1", "AlJazira", "b", t))
        assertNotEquals(a.bodyHash, b.bodyHash)
    }

    @Test
    fun blankSenderOrEmptyBody_rejected() {
        val t = Instant.EPOCH
        assertThrows(IllegalArgumentException::class.java) {
            AndroidSmsMapper.toRawSms(ProviderSmsRecord("1", "  ", "body", t))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidSmsMapper.toRawSms(ProviderSmsRecord("1", "AlJazira", "", t))
        }
    }

    @Test
    fun doesNotNormalizeBody() {
        val body = "  Keep\nExact  "
        val raw = AndroidSmsMapper.toRawSms(
            ProviderSmsRecord("9", "AlJazira", body, Instant.EPOCH),
        )
        assertEquals(body, raw.body)
        assertTrue(raw.body.startsWith("  "))
    }
}
