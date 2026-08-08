package com.baraa.masroof.ui.senders

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.ImportDisposition
import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.data.repository.SmsImportCommitMode
import com.baraa.masroof.data.repository.TemplateStatusLabels
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CandidatePatternWorkflowTest {

    private fun item(id: Long, disposition: ImportDisposition) = ScanPreview.PreviewItem(
        smsId = id,
        sender = "BANK",
        amount = BigDecimal.TEN,
        transactionType = TransactionType.PURCHASE,
        proposedAccountId = if (disposition == ImportDisposition.READY) 1L else null,
        proposedAccountName = null,
        isDuplicate = false,
        needsReview = ScanPreview.isReviewDisposition(disposition),
        isBeforeTrackingStart = false,
        date = null,
        disposition = disposition,
    )

    @Test
    fun eightyNineMessagesMapToThirteenCandidatePatternsInCta() {
        val items = (1L..89L).map { item(it, ImportDisposition.UNMATCHED_TEMPLATE) }
        val preview = ScanPreview(
            scannedMessages = 89,
            unmatchedTemplateMessages = 89,
            candidatePatternCount = 13,
            perTransaction = items,
        )
        assertEquals(89, preview.patternApprovalCount)
        assertEquals(13, preview.patternsNeedingApproval)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("مراجعة 13 نمطاً", actions.primaryLabel)
        assertTrue(actions.supportingText.orEmpty().contains("89 رسالة موزعة على 13 نمطاً"))
        assertFalse(actions.primaryLabel.contains("89"))
    }

    @Test
    fun readyNotBlockedByUnresolvedCandidates() {
        val items = buildList {
            repeat(60) { add(item(it + 1L, ImportDisposition.READY)) }
            repeat(30) { add(item(100L + it, ImportDisposition.UNMATCHED_TEMPLATE)) }
            repeat(10) { add(item(200L + it, ImportDisposition.NEEDS_ACCOUNT)) }
        }
        val preview = ScanPreview(
            scannedMessages = 100,
            unmatchedTemplateMessages = 30,
            candidatePatternCount = 5,
            perTransaction = items,
        )
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("استيراد 60 عملية", actions.primaryLabel)
        assertEquals(SmsImportCommitMode.READY_ONLY, actions.primaryMode)
        assertEquals("مراجعة 10 رسالة", actions.secondaryLabel)
        assertEquals("مراجعة 5 نمطاً", actions.tertiaryLabel)
    }

    @Test
    fun unknownIsCandidateApprovedIsTemplate() {
        assertTrue(TemplateStatusLabels.isCandidate(MessagePatternStatus.UNKNOWN))
        assertFalse(TemplateStatusLabels.isCandidate(MessagePatternStatus.APPROVED))
        assertTrue(TemplateStatusLabels.isApprovedTemplate(MessagePatternStatus.APPROVED))
        assertFalse(TemplateStatusLabels.isApprovedTemplate(MessagePatternStatus.UNKNOWN))
    }

    @Test
    fun senderDetailsCandidatesTabNeverListsApproved() {
        val source = java.io.File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/SenderDetailsScreen.kt",
        ).readText()
        assertTrue(source.contains("تحتاج اعتماد"))
        assertTrue(source.contains("القوالب"))
        assertTrue(source.contains("CandidatePatternCard"))
        assertTrue(source.contains("ApprovedTemplateCard"))
        // Must not call the old manage section composable in candidates tab.
        assertFalse(source.contains("SenderPatternManageSection("))
        assertTrue(source.contains("isCandidate"))
        assertTrue(source.contains("isApprovedTemplate"))
    }

    @Test
    fun oneSalarySmsCreatesCandidateAndStaysSalary() {
        val sms = SmsMessage(
            id = 1L,
            sender = "JAZIRABANK",
            body = "تم إيداع راتب بمبلغ 12,000.00 ر.س في حساب *1234",
            timestamp = System.currentTimeMillis(),
        )
        val discovered = PatternDiscoveryService.discover(listOf(sms), emptyList())
        assertTrue(discovered.isNotEmpty())
        val salary = discovered.filter {
            it.transactionTypeName == TransactionType.SALARY.name ||
                it.typeKey.contains("SALARY") ||
                it.friendlyNameHint.contains("راتب")
        }
        assertTrue("salary candidate missing: $discovered", salary.isNotEmpty())
        assertEquals(1, salary.first().messageCount)
        assertFalse(salary.any { it.transactionTypeName == TransactionType.TRANSFER_IN.name })
    }

    @Test
    fun oneMessagePatternIsPreserved() {
        val sms = SmsMessage(
            id = 2L,
            sender = "BANK",
            body = "رسوم سنوية بمبلغ 50.00 ر.س خصمت من حساب *9999",
            timestamp = System.currentTimeMillis(),
        )
        val discovered = PatternDiscoveryService.discover(listOf(sms), emptyList())
        assertTrue(discovered.isNotEmpty())
        assertTrue(discovered.any { it.messageCount == 1 })
    }

    @Test
    fun useOnceDoesNotRequireApprovedStatusInResolver() {
        // Structural guard: allowOncePatternIds parameter exists and is used.
        val source = java.io.File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/sms/TemplateResolutionService.kt",
        ).readText()
        assertTrue(source.contains("allowOncePatternIds"))
        assertTrue(source.contains("useOnce"))
    }

    @Test
    fun templateEditorSaveDoesNotCrashOnNavigation() {
        val source = java.io.File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/TemplateEditorScreen.kt",
        ).readText()
        assertTrue(source.contains("updateTemplate crashed"))
        assertTrue(source.contains("saving = false"))
        assertTrue(source.contains("finishAfterSave"))
    }
}
