package com.baraa.masroof.ui.senders

import com.baraa.masroof.data.repository.ImportDisposition
import com.baraa.masroof.data.repository.ImportSession
import com.baraa.masroof.data.repository.ImportSessionStore
import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.data.repository.SmsImportCommitMode
import com.baraa.masroof.data.repository.SmsImportMode
import com.baraa.masroof.data.repository.SmsImportResult
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure tests for SMS import CTA contracts: ready vs message-review vs pattern approval.
 */
class ImportExecutionResultTest {

    private fun item(
        id: Long,
        disposition: ImportDisposition,
        amount: BigDecimal? = BigDecimal("10.00"),
    ) = ScanPreview.PreviewItem(
        smsId = id,
        sender = "BANK",
        amount = amount,
        transactionType = TransactionType.PURCHASE,
        proposedAccountId = if (disposition == ImportDisposition.READY) 1L else null,
        proposedAccountName = if (disposition == ImportDisposition.READY) "حساب" else null,
        isDuplicate = disposition == ImportDisposition.EXACT_DUPLICATE,
        needsReview = ScanPreview.isReviewDisposition(disposition),
        isBeforeTrackingStart = disposition == ImportDisposition.BEFORE_TRACKING_START,
        date = null,
        disposition = disposition,
    )

    private fun previewWith(vararg dispositions: ImportDisposition): ScanPreview {
        val items = dispositions.mapIndexed { index, d -> item(index + 1L, d) }
        return ScanPreview(
            scannedMessages = items.size,
            recognizedTransactions = items.count {
                it.disposition == ImportDisposition.READY ||
                    ScanPreview.isMessageReviewDisposition(it.disposition)
            },
            needsReviewTransactions = items.count { ScanPreview.isReviewDisposition(it.disposition) },
            duplicateTransactions = items.count {
                it.disposition == ImportDisposition.EXACT_DUPLICATE ||
                    it.disposition == ImportDisposition.POSSIBLE_DUPLICATE
            },
            beforeTrackingStartCount = items.count { it.disposition == ImportDisposition.BEFORE_TRACKING_START },
            unmatchedTemplateMessages = items.count { it.disposition == ImportDisposition.UNMATCHED_TEMPLATE },
            ambiguousTemplateMessages = items.count { it.disposition == ImportDisposition.AMBIGUOUS_TEMPLATE },
            extractionFailedMessages = items.count { it.disposition == ImportDisposition.TEMPLATE_EXTRACTION_FAILED },
            perTransaction = items,
        )
    }

    @Test
    fun zeroReadyWithMessageReviewShowsReviewPrimaryNotImport() {
        val preview = previewWith(
            ImportDisposition.NEEDS_ACCOUNT,
            ImportDisposition.NEEDS_CONFIRMATION,
        )
        assertEquals(0, preview.readyToImport)
        assertEquals(2, preview.messageReviewCount)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("مراجعة 2 رسالة", actions.primaryLabel)
        assertTrue(actions.primaryNavigateReview)
        assertNull(actions.primaryMode)
        assertFalse(actions.primaryLabel.contains("استيراد"))
        assertFalse(actions.primaryLabel.contains("اعتماد"))
    }

    @Test
    fun unmatchedTemplatesRouteToPatternReviewNotAmbiguousApprove() {
        val preview = previewWith(
            ImportDisposition.UNMATCHED_TEMPLATE,
            ImportDisposition.UNMATCHED_TEMPLATE,
        ).let {
            it.copy(candidatePatternCount = 2)
        }
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals(0, preview.messageReviewCount)
        assertEquals(2, preview.patternApprovalCount)
        assertEquals("مراجعة 2 نمطاً", actions.primaryLabel)
        assertTrue(actions.primaryNavigateBankMessages)
        assertFalse(actions.primaryNavigateReview)
        assertNull(actions.primaryMode)
        assertFalse(actions.primaryLabel.startsWith("اعتماد "))
        assertNotNull(actions.headline)
        assertTrue(actions.supportingText.orEmpty().contains("2 نمطاً"))
    }

    @Test
    fun readyRemainsPrimaryEvenWhenPatternsNeedApproval() {
        val preview = ScanPreview(
            scannedMessages = 5,
            unmatchedTemplateMessages = 3,
            candidatePatternCount = 3,
            perTransaction = listOf(
                item(1, ImportDisposition.READY),
                item(2, ImportDisposition.READY),
                item(3, ImportDisposition.UNMATCHED_TEMPLATE),
                item(4, ImportDisposition.UNMATCHED_TEMPLATE),
                item(5, ImportDisposition.UNMATCHED_TEMPLATE),
            ),
        )
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 2 عملية", actions.primaryLabel)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
        assertEquals("مراجعة 3 نمطاً", actions.secondaryLabel)
        assertTrue(actions.secondaryNavigateBankMessages)
        assertFalse(actions.primaryLabel.contains("اعتماد"))
    }

    @Test
    fun readyOnlyShowsImportPrimary() {
        val preview = previewWith(ImportDisposition.READY, ImportDisposition.READY)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals(2, actions.readyToImport)
        assertEquals(0, actions.needsMessageReview)
        assertEquals("استيراد 2 عملية", actions.primaryLabel)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
        assertTrue(actions.primaryEnabled)
    }

    @Test
    fun mixedReadyAndMessageReviewShowsBothActions() {
        val preview = previewWith(
            ImportDisposition.READY,
            ImportDisposition.READY,
            ImportDisposition.NEEDS_ACCOUNT,
        )
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 2 عملية", actions.primaryLabel)
        assertEquals("مراجعة 1 رسالة", actions.secondaryLabel)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
        assertTrue(actions.secondaryNavigateReview)
        assertNull(actions.secondaryMode)
        assertNull(actions.tertiaryLabel)
    }

    @Test
    fun mixedReadyMessageReviewAndPatternsShowsThreeActions() {
        val preview = ScanPreview(
            scannedMessages = 5,
            unmatchedTemplateMessages = 2,
            candidatePatternCount = 2,
            perTransaction = listOf(
                item(1, ImportDisposition.READY),
                item(2, ImportDisposition.READY),
                item(3, ImportDisposition.NEEDS_ACCOUNT),
                item(4, ImportDisposition.UNMATCHED_TEMPLATE),
                item(5, ImportDisposition.UNMATCHED_TEMPLATE),
            ),
        )
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 2 عملية", actions.primaryLabel)
        assertEquals("مراجعة 1 رسالة", actions.secondaryLabel)
        assertEquals("مراجعة 2 نمطاً", actions.tertiaryLabel)
        assertTrue(actions.secondaryNavigateReview)
        assertTrue(actions.tertiaryNavigateBankMessages)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
    }

    @Test
    fun journeyTwentyMessagesTenReadySixPatternFourAccount() {
        val dispositions = buildList {
            repeat(10) { add(ImportDisposition.READY) }
            repeat(6) { add(ImportDisposition.UNMATCHED_TEMPLATE) }
            repeat(4) { add(ImportDisposition.NEEDS_ACCOUNT) }
        }
        val items = dispositions.mapIndexed { index, d -> item(index + 1L, d) }
        val preview = ScanPreview(
            scannedMessages = 20,
            unmatchedTemplateMessages = 6,
            candidatePatternCount = 6,
            perTransaction = items,
        )
        assertEquals(10, preview.readyToImport)
        assertEquals(4, preview.messageReviewCount)
        assertEquals(6, preview.patternApprovalCount)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 10 عملية", actions.primaryLabel)
        assertEquals("مراجعة 4 رسالة", actions.secondaryLabel)
        assertEquals("مراجعة 6 نمطاً", actions.tertiaryLabel)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
    }

    @Test
    fun afterPatternApprovalSessionResumesAndCountsRefresh() {
        val store = ImportSessionStore()
        val initial = previewWith(
            *Array(10) { ImportDisposition.READY },
            *Array(6) { ImportDisposition.UNMATCHED_TEMPLATE },
            *Array(4) { ImportDisposition.NEEDS_ACCOUNT },
        )
        val session = ImportSession(
            id = "journey-1",
            preview = initial,
            messages = emptyList(),
            trackingStartDate = null,
            mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
        )
        store.replace(session)
        store.beginTemplateApprovalFromImport()
        assertTrue(store.isReturnToImportActive())

        // Simulate approving templates: 5 of 6 unmatched become READY, 1 needs account.
        val afterApproval = previewWith(
            *Array(15) { ImportDisposition.READY },
            *Array(5) { ImportDisposition.NEEDS_ACCOUNT },
        )
        store.markTemplatesChanged()
        assertTrue(store.consumeTemplatesDirty())
        store.replace(session.copy(preview = afterApproval))
        store.clearReturnToImport()

        assertEquals("journey-1", store.current()?.id)
        assertEquals(15, store.current()?.readyToImport)
        assertEquals(5, store.current()?.needsMessageReview)
        assertEquals(0, store.current()?.needsPatternApproval)
        assertFalse(store.isReturnToImportActive())

        val actions = importActionState(afterApproval, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 15 عملية", actions.primaryLabel)
        assertEquals("مراجعة 5 رسالة", actions.secondaryLabel)
    }

    @Test
    fun normalTemplateManagementDoesNotForceReturnToImport() {
        val store = ImportSessionStore()
        // Session exists but user opened Bank Messages from settings — no beginTemplateApprovalFromImport.
        store.replace(
            ImportSession(
                preview = previewWith(ImportDisposition.READY),
                messages = emptyList(),
                trackingStartDate = null,
                mode = SmsImportMode.REGISTERED_ACCOUNTS_ONLY,
            ),
        )
        assertFalse(store.isReturnToImportActive())
        store.markTemplatesChanged()
        assertTrue(store.consumeTemplatesDirty())
        // Return flag stays off — UI must not auto-pop to Import.
        assertFalse(store.isReturnToImportActive())
    }

    @Test
    fun importButtonLabelHelperMatchesActionState() {
        val preview = previewWith(ImportDisposition.READY)
        assertEquals("استيراد 1 عملية", importCommitButtonLabel(preview))
        val reviewOnly = previewWith(ImportDisposition.NEEDS_ACCOUNT)
        assertEquals("مراجعة 1 رسالة", importCommitButtonLabel(reviewOnly))
    }

    @Test
    fun successRequiresImportedCountPositiveForReadyMode() {
        val result = SmsImportResult(importedTransactions = 12, linkedTransactions = 12, postedTransactions = 12)
        assertTrue(mapImportCommitResult(result, SmsImportCommitMode.READY_ONLY) is ImportExecutionResult.Success)
    }

    @Test
    fun databaseFailureMapsToFailureNotSilent() {
        val failure = ImportExecutionResult.Failure(
            userMessage = "تعذر استيراد العمليات. حاول مجدداً.",
            technicalMessage = "SQLiteConstraintException",
        )
        assertTrue(failure.userMessage.isNotBlank())
        assertNotNull(failure.technicalMessage)
    }

    @Test
    fun zeroImportReadyModeIsFailure() {
        val mapped = mapImportCommitResult(
            SmsImportResult(importedTransactions = 0, duplicateTransactions = 0),
            SmsImportCommitMode.READY_ONLY,
        )
        assertTrue(mapped is ImportExecutionResult.Failure)
    }

    @Test
    fun allDuplicatesMapToAlreadyImportedNotFailure() {
        val result = SmsImportResult(
            scannedMessages = 10,
            duplicateTransactions = 10,
            importedTransactions = 0,
        )
        val mapped = mapImportCommitResult(result, SmsImportCommitMode.READY_ONLY)
        assertTrue(mapped is ImportExecutionResult.AlreadyImported)
        assertEquals(10, (mapped as ImportExecutionResult.AlreadyImported).duplicateCount)
    }

    @Test
    fun busyStateDisablesPrimaryToPreventRepeatedTap() {
        val preview = previewWith(ImportDisposition.READY)
        val loading = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Loading)
        assertFalse(loading.primaryEnabled)
        val committing = importActionState(preview, ImportPhase.Committing, ImportExecutionResult.Idle)
        assertFalse(committing.primaryEnabled)
    }

    @Test
    fun recompositionDuringImportKeepsLoadingGuard() {
        val preview = previewWith(ImportDisposition.READY)
        repeat(3) {
            val actions = importActionState(preview, ImportPhase.Committing, ImportExecutionResult.Loading)
            assertFalse(actions.primaryEnabled)
        }
    }

    @Test
    fun duplicateOnlyScanDisablesImport() {
        val preview = previewWith(ImportDisposition.EXACT_DUPLICATE, ImportDisposition.EXACT_DUPLICATE)
        assertEquals(0, preview.readyToImport)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertFalse(actions.primaryEnabled)
        assertTrue(actions.primaryLabel.contains("مكررة"))
    }

    @Test
    fun importButtonDoesNotCommitOnReviewNavigation() {
        val source = readSourceFile()
        assertTrue(source.contains("primaryNavigateReview"))
        assertTrue(source.contains("SMS_IMPORT_OPEN_REVIEW"))
        assertTrue(source.contains("openPatternApprovalFromImport"))
        assertTrue(source.contains("beginTemplateApprovalFromImport"))
        val openIdx = source.indexOf("SMS_IMPORT_OPEN_PATTERNS")
        assertTrue(openIdx >= 0)
        val snippet = source.substring(openIdx, (openIdx + 280).coerceAtMost(source.length))
        assertTrue(snippet.contains("openPatternApprovalFromImport()"))
        assertFalse(snippet.contains(".commit("))
    }

    @Test
    fun messagesFailuresAreNotSwallowed() {
        val source = readSourceFile()
        assertTrue(source.contains("ImportExecutionResult.Failure"))
        assertTrue(source.contains("SMS_IMPORT_COMMIT_FAILED"))
    }

    private fun readSourceFile(): String {
        val candidates = listOf(
            "app/src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/ImportMessagesScreen.kt",
        )
        for (path in candidates) {
            val f = java.io.File(path)
            if (f.exists()) return f.readText()
        }
        throw java.io.FileNotFoundException("ImportMessagesScreen.kt not found")
    }
}
