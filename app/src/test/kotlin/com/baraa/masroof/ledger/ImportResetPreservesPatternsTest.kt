package com.baraa.masroof.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportResetPreservesPatternsTest {
    @Test
    fun clearImportedLedger_preservesPatternDefinitionsAndDoesNotDeleteThem() {
        val source = File("src/main/kotlin/com/baraa/masroof/ledger/ImportResetService.kt").readText()
        assertTrue(source.contains("clearImportedLedger"))
        assertTrue(source.contains("message pattern definitions") || source.contains("sender profiles"))
        assertFalse(source.contains("messagePatternDefinitionDao().delete"))
        assertFalse(source.contains("DELETE FROM message_pattern_definitions"))
        assertFalse(source.contains("DELETE FROM sender_profiles"))
    }
}
