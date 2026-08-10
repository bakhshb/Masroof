package com.baraa.masroof.sms.hash

import java.security.MessageDigest

/**
 * Deterministic SHA-256 of the exact UTF-8 raw SMS body (lowercase hex).
 *
 * Hashes the persisted body — never a normalized form.
 */
object SmsBodyHasher {
    fun sha256Hex(rawBody: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawBody.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
