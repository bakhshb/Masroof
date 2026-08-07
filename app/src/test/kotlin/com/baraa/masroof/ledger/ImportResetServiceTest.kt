package com.baraa.masroof.ledger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportResetServiceTest {
    @Test
    fun serviceClearsLedgerTablesAndPreservesAccountsInCode() {
        val source = File(
            "app/src/main/kotlin/com/baraa/masroof/ledger/ImportResetService.kt",
        ).takeIf { it.exists() }
            ?: File("/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ledger/ImportResetService.kt")
        val text = source.readText()
        assertTrue(text.contains("deleteAllPostings"))
        assertTrue(text.contains("deleteAllJournals"))
        assertTrue(text.contains("transactionDao().deleteAll()"))
        assertFalse("must not delete financial accounts", text.contains("financialAccountDao().delete"))
        assertFalse("must not wipe identifiers", text.contains("accountIdentifierDao().delete"))
        assertTrue(text.contains("Preserves"))
    }

    @Test
    fun diagnosticsAndImportScreensExposeClearAction() {
        val diagnostics = File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/diagnostics/DiagnosticsScreen.kt",
        ).readText()
        val importScreen = File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
        ).readText()
        assertTrue(diagnostics.contains("مسح العمليات المستوردة"))
        assertTrue(importScreen.contains("مسح العمليات المستوردة"))
        assertTrue(diagnostics.contains("importResetService.clearImportedLedger"))
        assertTrue(importScreen.contains("importResetService.clearImportedLedger"))
    }
}
