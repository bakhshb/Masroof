package com.baraa.masroof.application.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppLogRedactorTest {
    @Test
    fun redact_masksClassicPat() {
        val sanitized = AppLogRedactor.redact("token ghp_1234567890123456789012345678901234")
        assertFalse(sanitized.contains("ghp_1234567890123456789012345678901234"))
    }

    @Test
    fun redact_masksFineGrainedPat() {
        val sanitized = AppLogRedactor.redact("Bearer github_pat_abcdefghijklmnopqrstuvwxyz")
        assertFalse(sanitized.contains("github_pat_abcdefghijklmnopqrstuvwxyz"))
    }
}
