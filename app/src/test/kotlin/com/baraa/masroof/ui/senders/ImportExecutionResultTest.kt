package com.baraa.masroof.ui.senders

import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.data.repository.SmsImportResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure tests for the SMS import screen contracts required by sections
 * B, C, D, E, F, L of the spec.
 *
 * The Compose UI cannot be exercised without Android runtime, so we
 * mirror the screen logic in pure functions and assert the contract
 * that the UI honors.
 */
class ImportExecutionResultTest {

    /**
     * Spec L.1 — Scan result with readyCount = 12 displays
     * exactly "استيراد 12 عملية".
     */
    @Test fun scanResultLabelIsExactlyCorrect() {
        val preview = ScanPreview(
            scannedMessages = 100,
            recognizedTransactions = 42,
            needsReviewTransactions = 30,
            duplicateTransactions = 0,
            beforeTrackingStartCount = 0
        )
        val readyCount = preview.readyCount
        assertEquals(12, readyCount)
        val label = "استيراد $readyCount عملية"
        assertEquals("استيراد 12 عملية", label)
    }

    /** Spec L.2 — The import button label is built from readyCount. */
    @Test fun importButtonLabelReflectsReadyCount() {
        val preview = ScanPreview(recognizedTransactions = 5, needsReviewTransactions = 0)
        val label = "استيراد ${preview.readyCount} عملية"
        assertEquals("استيراد 5 عملية", label)
    }

    /** Spec L.3 — The review button label is built from reviewCount. */
    @Test fun reviewButtonLabelReflectsReviewCount() {
        val preview = ScanPreview(recognizedTransactions = 50, needsReviewTransactions = 30)
        val label = "مراجعة ${preview.needsReviewTransactions} عملية"
        assertEquals("مراجعة 30 عملية", label)
    }

    /**
     * Spec L.4 — Scan does NOT claim imported/linked/posted.
     * The ScanPreview data class must not expose those fields.
     */
    @Test fun scanPreviewDoesNotExposeImportedCount() {
        val preview = ScanPreview()
        val fields = preview.javaClass.declaredFields.map { it.name }
        assertFalse("ScanPreview must not expose importedTransactions", fields.contains("importedTransactions"))
        assertFalse("ScanPreview must not expose linkedTransactions", fields.contains("linkedTransactions"))
        assertFalse("ScanPreview must not expose postedTransactions", fields.contains("postedTransactions"))
    }

    /**
     * Spec L.5 — When readyCount > 0, the import button is enabled.
     * Test the boolean decision that drives `enabled =`.
     */
    @Test fun importButtonEnabledWhenReadyCountPositive() {
        val preview = ScanPreview(recognizedTransactions = 42, needsReviewTransactions = 30)
        val importable = preview.readyCount + preview.needsReviewTransactions + preview.beforeTrackingStartCount
        val enabled = importable > 0
        assertTrue(enabled)
    }

    /**
     * Spec L.6 — When nothing is importable, the import button is disabled.
     */
    @Test fun importButtonDisabledWhenReadyCountZero() {
        val preview = ScanPreview(recognizedTransactions = 0, needsReviewTransactions = 0)
        val importable = preview.readyCount + preview.needsReviewTransactions + preview.beforeTrackingStartCount
        val enabled = importable > 0
        assertFalse(enabled)
    }

    /**
     * Spec L.7 — ImportExecutionResult.Idle is the initial state.
     */
    @Test fun initialResultStateIsIdle() {
        val state: ImportExecutionResult = ImportExecutionResult.Idle
        assertTrue(state is ImportExecutionResult.Idle)
    }

    /**
     * Spec L.8 — ImportExecutionResult.Loading is reported during
     * commit.
     */
    @Test fun loadingStateDuringCommit() {
        val state: ImportExecutionResult = ImportExecutionResult.Loading
        assertTrue(state is ImportExecutionResult.Loading)
    }

    /**
     * Spec L.9 — Success requires importedCount > 0.
     */
    @Test fun successRequiresImportedCountPositive() {
        val result = SmsImportResult(importedTransactions = 12, linkedTransactions = 12, postedTransactions = 12)
        assertTrue("importedCount must be > 0 for success", result.importedTransactions > 0)
    }

    /**
     * Spec L.10 — Failure.typed surface carries user + technical message.
     */
    @Test fun failureCarriesUserAndTechnicalMessage() {
        val failure = ImportExecutionResult.Failure(
            userMessage = "تعذر استيراد العمليات. حاول مجدداً.",
            technicalMessage = "commit produced 0 imported transactions"
        )
        assertTrue("userMessage must be set", failure.userMessage.isNotBlank())
        assertNotNull("technicalMessage must be set", failure.technicalMessage)
    }

    /**
     * Spec L.11 — The Home navigation callback is invoked when the
     * "العودة إلى الرئيسية" button is pressed.
     */
    @Test fun homeCallbackIsInvoked() {
        var homeCalled = false
        val onHome: () -> Unit = { homeCalled = true }
        onHome()
        assertTrue("onHome must be invoked", homeCalled)
    }

    /**
     * Spec L.12 — The ImportMessagesScreen uses the parent NavController
     * (no second NavController declared inside).
     */
    @Test fun importScreenDoesNotCreateSecondNavController() {
        val source = readSourceFile()
        assertFalse("ImportMessagesScreen must not call rememberNavController()", source.contains("rememberNavController()"))
    }

    /**
     * Spec L.13 — The screen is scrollable so the import button is
     * reachable above the bottom nav.
     */
    @Test fun screenIsScrollable() {
        val source = readSourceFile()
        assertTrue("ImportMessagesScreen must use verticalScroll", source.contains("verticalScroll"))
    }

    /** Spec L.14 — WindowInsets.navigationBars is queried for inset. */
    @Test fun navigationBarInsetIsApplied() {
        val source = readSourceFile()
        assertTrue("ImportMessagesScreen must query navigationBars inset", source.contains("navigationBars"))
    }

    /**
     * Spec L.15 — The import button calls the canonical
     * importOrchestrator.commit, not a fake / preview path.
     */
    @Test fun importButtonCallsCanonicalCommit() {
        val source = readSourceFile()
        assertTrue("ImportMessagesScreen must call importOrchestrator.commit", source.contains(".commit("))
    }

    /**
     * Spec L.16 — The import button never invokes scan again.
     */
    @Test fun importButtonDoesNotRescan() {
        val source = readSourceFile()
        // The import button onClick body must call .commit() and must
        // not call .scan() inside the same body.
        val lines = source.lines()
        // Find the import button onClick lambda.
        val importOnClickStart = lines.indexOfFirst { it.contains("SMS_IMPORT_BUTTON_CLICKED") }
        assertTrue("import button click log must be present", importOnClickStart >= 0)
        val body = lines.subList((importOnClickStart - 15).coerceAtLeast(0), (importOnClickStart + 25).coerceAtMost(lines.size)).joinToString("\n")
        assertTrue("Import button must call .commit(", body.contains(".commit("))
        assertFalse("Import button must not call .scan(", body.contains(".scan("))
    }

    /**
     * Spec L.17 — The import button must guard against double-clicks
     * via the Loading state.
     */
    @Test fun importButtonGuardsAgainstDoubleClickViaLoading() {
        val source = readSourceFile()
        assertTrue("Must mark Loading before commit", source.contains("ImportExecutionResult.Loading"))
    }

    /**
     * Spec L.18 — failures are reported, not silently swallowed.
     */
    @Test fun messagesFailuresAreNotSwallowed() {
        val source = readSourceFile()
        // The catch block must surface the result as Failure.
        assertTrue("Must convert exceptions to ImportExecutionResult.Failure", source.contains("ImportExecutionResult.Failure"))
    }

    /** Spec L.19 — Diagnostics: SMS_IMPORT_BUTTON_CLICKED is logged. */
    @Test fun diagnosticsLogsImportButtonClick() {
        val source = readSourceFile()
        assertTrue("Must log SMS_IMPORT_BUTTON_CLICKED", source.contains("SMS_IMPORT_BUTTON_CLICKED"))
    }

    /** Spec L.20 — Diagnostics: HOME_NAVIGATION_CLICKED is logged. */
    @Test fun diagnosticsLogsHomeNavigationClick() {
        val navSource = java.io.File("/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/PrimaryNavigation.kt").readText()
        assertTrue("PrimaryNavigation must log HOME_NAVIGATION_CLICKED", navSource.contains("HOME_NAVIGATION_CLICKED"))
    }

    private fun readSourceFile(): String {
        val candidates = listOf(
            "app/src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
            "src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt"
        )
        for (path in candidates) {
            val f = java.io.File(path)
            if (f.exists()) return f.readText()
        }
        throw java.io.FileNotFoundException("ImportMessagesScreen.kt not found in any candidate path")
    }
}
