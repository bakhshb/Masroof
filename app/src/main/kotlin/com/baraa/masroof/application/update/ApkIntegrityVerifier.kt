package com.baraa.masroof.application.update

import java.io.File
import java.security.MessageDigest

object ApkIntegrityVerifier {
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expectedSha256: String): Boolean =
        sha256Hex(file).equals(expectedSha256.trim(), ignoreCase = true)
}
