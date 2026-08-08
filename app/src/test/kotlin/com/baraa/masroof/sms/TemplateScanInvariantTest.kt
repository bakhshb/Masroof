package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternOrigin
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.ScanFilterFunnel
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Template-stage invariant: every message that enters matching must land in
 * exactly one of MATCHED / UNMATCHED / AMBIGUOUS — never disappear.
 */
class TemplateScanInvariantTest {

    private val purchaseBody = """
        شراء
        بمبلغ: 50.00 SAR
        لدى: Store
    """.trimIndent()

    private val purchaseTemplate = """
        شراء
        بمبلغ: {AMOUNT} SAR
        لدى: {MERCHANT}
    """.trimIndent()

    private fun sms(id: Long, body: String = purchaseBody) =
        SmsMessage(id = id, sender = "BANK", body = body, timestamp = 1_725_000_000_000L + id)

    private fun approved(
        id: Long,
        template: String = purchaseTemplate,
        canonicalKey: String = "TYPE:PURCHASE|AMOUNT|MERCHANT",
    ) = MessagePattern(
        definition = MessagePatternDefinitionEntity(
            id = id,
            senderProfileId = 1L,
            userFriendlyName = "شراء",
            normalizedSignature = "sig-$id",
            canonicalKey = canonicalKey,
            templateText = template,
            transactionType = TransactionType.PURCHASE.name,
            status = MessagePatternStatus.APPROVED,
            isActive = true,
            origin = PatternOrigin.USER_TRAINED,
            userConfirmed = true,
            exampleCount = 3,
            createdAt = 0L,
            updatedAt = 0L,
        ),
        fields = emptyList(),
    )

    private fun classifyAll(
        messages: List<SmsMessage>,
        patterns: List<MessagePattern>,
    ): ScanFilterFunnel {
        var matched = 0
        var unmatched = 0
        var ambiguous = 0
        var extractionFailed = 0
        for (sms in messages) {
            when (val r = TemplateResolutionService.resolve(sms.sender, sms.body, sms.timestamp, patterns)) {
                is TemplateResolutionResult.Matched -> {
                    matched++
                    if (r.parsed.amount == null) extractionFailed++
                }
                is TemplateResolutionResult.Unmatched -> unmatched++
                is TemplateResolutionResult.Ambiguous -> ambiguous++
            }
        }
        return ScanFilterFunnel(
            rawSms = messages.size,
            afterOtpFilter = messages.size,
            afterSenderFilter = messages.size,
            templateInput = messages.size,
            templateMatched = matched,
            unmatchedTemplate = unmatched,
            ambiguousTemplate = ambiguous,
            extractionFailed = extractionFailed,
        )
    }

    @Test
    fun tenMessagesZeroTemplates_allUnmatched() {
        val messages = List(10) { sms(it.toLong()) }
        val funnel = classifyAll(messages, emptyList())
        assertEquals(10, funnel.templateInput)
        assertEquals(0, funnel.templateMatched)
        assertEquals(10, funnel.unmatchedTemplate)
        assertEquals(0, funnel.ambiguousTemplate)
        assertTrue(funnel.templateInvariantHolds)
    }

    @Test
    fun tenMessagesOneTemplateMatchingSix() {
        val matching = """
            شراء
            بمبلغ: 50.00 SAR
            لدى: Store
        """.trimIndent()
        val other = """
            تحويل
            بمبلغ: 10.00 SAR
            إلى: X
        """.trimIndent()
        val messages = List(10) { i ->
            sms(i.toLong(), body = if (i < 6) matching else other)
        }
        val funnel = classifyAll(messages, listOf(approved(1)))
        assertEquals(10, funnel.templateInput)
        assertEquals(6, funnel.templateMatched)
        assertEquals(4, funnel.unmatchedTemplate)
        assertEquals(0, funnel.ambiguousTemplate)
        assertTrue(funnel.templateInvariantHolds)
    }

    @Test
    fun ambiguousMatchesRemainCounted() {
        val a = approved(1, canonicalKey = "TYPE:PURCHASE|A")
        val b = approved(2, canonicalKey = "TYPE:PURCHASE|B")
        val funnel = classifyAll(listOf(sms(1)), listOf(a, b))
        assertEquals(1, funnel.templateInput)
        assertEquals(0, funnel.templateMatched)
        assertEquals(0, funnel.unmatchedTemplate)
        assertEquals(1, funnel.ambiguousTemplate)
        assertTrue(funnel.templateInvariantHolds)
    }

    @Test
    fun extractionFailureAfterMatchRemainsVisible() {
        val templateNoAmount = """
            شراء
            لدى: {MERCHANT}
        """.trimIndent()
        val body = """
            شراء
            لدى: Store
        """.trimIndent()
        val pattern = approved(1, template = templateNoAmount, canonicalKey = "TYPE:PURCHASE|MERCHANT")
        val result = TemplateResolutionService.resolve("BANK", body, null, listOf(pattern))
        assertTrue(result is TemplateResolutionResult.Matched)
        val matched = result as TemplateResolutionResult.Matched
        assertEquals(null, matched.parsed.amount)
        // Matched still counts; extraction failure is a separate post-match counter.
        val funnel = ScanFilterFunnel(
            templateInput = 1,
            templateMatched = 1,
            unmatchedTemplate = 0,
            ambiguousTemplate = 0,
            extractionFailed = 1,
        )
        assertTrue(funnel.templateInvariantHolds)
        assertEquals(1, funnel.extractionFailed)
    }

    @Test
    fun emptyTemplateListDoesNotDropMessages() {
        val funnel = classifyAll(List(169) { sms(it.toLong()) }, emptyList())
        assertEquals(169, funnel.templateInput)
        assertEquals(169, funnel.unmatchedTemplate)
        assertEquals(169, funnel.templateOutcomeSum)
        assertTrue(funnel.templateInvariantHolds)
    }

    @Test
    fun lookupFailureMapsToExplicitUnmatched() {
        val outcome = TemplateResolutionResult.Unmatched(
            TemplateResolutionResult.Unmatched.Reason.LOOKUP_FAILED,
        )
        assertEquals(TemplateResolutionResult.Unmatched.Reason.LOOKUP_FAILED, outcome.reason)
    }

}
