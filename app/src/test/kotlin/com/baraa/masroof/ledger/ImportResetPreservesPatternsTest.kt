package com.baraa.masroof.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportResetPreservesPatternsTest {
    @Test
    fun clearImportedLedger_doesNotMentionDeletingSenderMessagePatterns() {
        val source = File("src/main/kotlin/com/baraa/masroof/ledger/ImportResetService.kt").readText()
        assertTrue(source.contains("clearImportedLedger"))
        assertTrue(source.contains("sender_message_patterns") || source.contains("teach-by-example"))
        assertFalse(source.contains("senderMessagePatternDao().delete"))
        assertFalse(source.contains("DELETE FROM sender_message_patterns"))
    }
}
