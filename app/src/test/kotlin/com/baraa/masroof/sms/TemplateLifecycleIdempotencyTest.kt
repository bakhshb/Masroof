package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternOrigin
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.ScanPreview
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.senders.ImportExecutionResult
import com.baraa.masroof.ui.senders.ImportPhase
import com.baraa.masroof.ui.senders.importActionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Template lifecycle: approval must be idempotent and must not rediscover
 * already-approved structures. Unregistered senders never create candidates.
 */
class TemplateLifecycleIdempotencyTest {

    private fun approvedPattern(
        id: Long,
        senderProfileId: Long,
        bodySample: String,
        type: TransactionType = TransactionType.PURCHASE,
    ): MessagePattern {
        val built = MessageTemplateEngine.buildFromSms(bodySample)
        val key = TemplateCanonicalizer.canonicalKey(
            built.templateText,
            built.signature,
            type.name,
        )
        return MessagePattern(
            MessagePatternDefinitionEntity(
                id = id,
                senderProfileId = senderProfileId,
                userFriendlyName = built.displayName,
                normalizedSignature = built.signature,
                canonicalKey = key,
                templateText = built.templateText,
                transactionType = type.name,
                direction = built.direction,
                status = MessagePatternStatus.APPROVED,
                isActive = true,
                version = 1,
                origin = PatternOrigin.USER_TRAINED,
                confidence = 100,
                userConfirmed = true,
                exampleCount = 1,
                activeFrom = 1L,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            emptyList(),
        )
    }

    @Test
    fun approvedTemplateMatchesEquivalentSmsWithDifferentValues() {
        val sample = """
            شراء عبر نقاط البيع
            بطاقة ائتمانية: 1234
            لدى: MERCHANT_A
            بمبلغ: 10.00
            في: 01/01/2024 10:00
        """.trimIndent()
        val approved = approvedPattern(1, 10, sample, TransactionType.PURCHASE)
        val next = """
            شراء عبر نقاط البيع
            بطاقة ائتمانية: 5678
            لدى: MERCHANT_B
            بمبلغ: 99.50
            في: 02/02/2024 11:30
        """.trimIndent()
        val result = TemplateResolutionService.resolve(
            sender = "BANK",
            body = next,
            smsTimestampMillis = System.currentTimeMillis(),
            patterns = listOf(approved),
        )
        assertTrue(
            "expected matched against approved template, got $result",
            result is TemplateResolutionResult.Matched,
        )
    }

    @Test
    fun rediscoveryDoesNotCreateCandidateWhenApprovedExists() {
        val sample = """
            شراء عبر نقاط البيع
            بطاقة ائتمانية: 1234
            لدى: STORE
            بمبلغ: 25.00
            في: 01/01/2024 10:00
        """.trimIndent()
        val approved = approvedPattern(7, 3, sample, TransactionType.PURCHASE)
        val messages = (1..5).map { i ->
            SmsMessage(
                id = i.toLong(),
                sender = "BANK",
                body = """
                    شراء عبر نقاط البيع
                    بطاقة ائتمانية: ${1000 + i}
                    لدى: STORE_$i
                    بمبلغ: ${10 + i}.00
                    في: 0$i/01/2024 10:0$i
                """.trimIndent(),
                timestamp = System.currentTimeMillis(),
            )
        }
        val discovered = PatternDiscoveryService.discover(messages, listOf(approved.definition))
        val pending = discovered.filter {
            !it.looksLikeOtpOrMarketing &&
                !it.looksLikeNonFinancial &&
                it.matchedPatternStatus != MessagePatternStatus.APPROVED
        }
        assertEquals("approved structures must not rediscover as candidates", 0, pending.size)
        assertTrue(discovered.any { it.matchedPatternStatus == MessagePatternStatus.APPROVED })
    }

    @Test
    fun optionalBalanceLineMissingStillMatchesApproved() {
        val withBalance = """
            شراء عبر نقاط البيع
            بطاقة ائتمانية: 1234
            لدى: CAFE
            بمبلغ: 12.00
            في: 01/01/2024 10:00
            الرصيد المتاح: 500.00
        """.trimIndent()
        val approved = approvedPattern(2, 1, withBalance, TransactionType.PURCHASE)
        val withoutBalance = """
            شراء عبر نقاط البيع
            بطاقة ائتمانية: 9999
            لدى: CAFE2
            بمبلغ: 8.00
            في: 03/03/2024 12:00
        """.trimIndent()
        // Optional anchors are absent without creating a new variant.
        val result = TemplateResolutionService.resolve(
            sender = "BANK",
            body = withoutBalance,
            smsTimestampMillis = 1L,
            patterns = listOf(approved),
        )
        assertTrue(result is TemplateResolutionResult.Matched)
    }

    @Test
    fun zeroCandidateCountDoesNotInventPatternCta() {
        val preview = ScanPreview(
            scannedMessages = 76,
            unmatchedTemplateMessages = 76,
            candidatePatternCount = 0,
            candidateDiagnostics = emptyList(),
            perTransaction = (1L..76L).map {
                ScanPreview.PreviewItem(
                    smsId = it,
                    sender = "BANK",
                    amount = null,
                    transactionType = TransactionType.PURCHASE,
                    proposedAccountId = null,
                    proposedAccountName = null,
                    isDuplicate = false,
                    needsReview = true,
                    isBeforeTrackingStart = false,
                    date = null,
                    disposition = com.baraa.masroof.data.repository.ImportDisposition.UNMATCHED_TEMPLATE,
                )
            },
        )
        assertEquals(0, preview.patternsNeedingApproval)
        assertEquals(76, preview.patternApprovalCount)
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertFalse(actions.primaryLabel.contains("25"))
        assertFalse(actions.primaryLabel.matches(Regex("مراجعة \\d+ نمطاً")))
        assertTrue(
            actions.headline.orEmpty().contains("لا توجد أنماط جديدة") ||
                actions.supportingText.orEmpty().contains("ليست أنماطاً جديدة"),
        )
    }

    @Test
    fun candidateCountUsesDiagnosticsNotGateMessages() {
        val preview = ScanPreview(
            scannedMessages = 89,
            unmatchedTemplateMessages = 89,
            candidatePatternCount = 3,
            perTransaction = (1L..89L).map {
                ScanPreview.PreviewItem(
                    smsId = it,
                    sender = "BANK",
                    amount = null,
                    transactionType = TransactionType.PURCHASE,
                    proposedAccountId = null,
                    proposedAccountName = null,
                    isDuplicate = false,
                    needsReview = true,
                    isBeforeTrackingStart = false,
                    date = null,
                    disposition = com.baraa.masroof.data.repository.ImportDisposition.UNMATCHED_TEMPLATE,
                )
            },
        )
        val actions = importActionState(preview, ImportPhase.Idle, ImportExecutionResult.Idle)
        assertEquals("مراجعة 3 نمطاً", actions.primaryLabel)
        assertTrue(actions.supportingText.orEmpty().contains("89 رسالة موزعة على 3 نمطاً"))
    }

    @Test
    fun salaryStaysSeparateFromTransferInOnRediscovery() {
        val salaryBody = "تم إيداع راتب بمبلغ 12000.00 ر.س في حساب *4321"
        val approved = approvedPattern(9, 1, salaryBody, TransactionType.SALARY)
        val again = PatternDiscoveryService.discover(
            listOf(SmsMessage(1, "BANK", salaryBody, 1L)),
            listOf(approved.definition),
        )
        assertTrue(again.any { it.matchedPatternStatus == MessagePatternStatus.APPROVED })
        assertFalse(
            again.any {
                it.matchedPatternStatus != MessagePatternStatus.APPROVED &&
                    it.transactionTypeName == TransactionType.TRANSFER_IN.name
            },
        )
    }
}
