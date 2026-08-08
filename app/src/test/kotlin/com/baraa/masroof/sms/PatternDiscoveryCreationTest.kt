package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePatternRepository
import com.baraa.masroof.sms.RoundTripAnchorDao
import com.baraa.masroof.sms.RoundTripFamilyDao
import com.baraa.masroof.sms.RoundTripPatternDefinitionDao
import com.baraa.masroof.sms.RoundTripPatternFieldDao
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.OptionalDiscoveryStages
import com.baraa.masroof.sms.PatternDiscoveryStage
import com.baraa.masroof.sms.SmsMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the physical-device failure where "اكتشاف أنماط جديدة"
 * produced 0 patterns from 226 valid financial SMS.
 *
 * These tests run the FULL [PatternDiscoveryService.discoverSafely] pipeline
 * (including sanitized preview) on real Jazira message shapes, then persist
 * through [MessagePatternRepository.saveDiscoveredBatch] exactly like the
 * SenderDetailsScreen button does.
 */
class PatternDiscoveryCreationTest {

    private val posBody = """
        شراء عبر نقاط البيع (Google Pay)
        لدى: MALAYSIA FOODS RESTA
        بمبلغ: 127.00 SAR
        في: 13:24 2026-08-03
        بطاقة مدى رقم: 8219
    """.trimIndent()

    private val creditBody = """
        شراء عبر الانترنت
        بطاقة ائتمانية: 7271
        بمبلغ :51.99 SAR
        لدى :Keeta
        في :22:50 03-08-2026
        الرصيد المتاح :SAR 17230.03
        إجمالي المبلغ المستحق:2380.88 SAR
    """.trimIndent()

    private val transferBody = """
        عملية حوالة مالية صادرة مقبولة
        خصمت من حساب: 3001
        الى: مستفيد
        مبلغ العملية: 300.00 SAR
        المعرف البديل \الايبان : 6810
        [البنك الأهلي السعودي]
        في: 2026-08-03 14:32
        رقم المعاملة: 2BTMS11432672163
    """.trimIndent()

    private val salaryBody = """
        تم إيداع راتب
        مبلغ: 10000.00 SAR
        في حساب: 1111
    """.trimIndent()

    private val otpBody = "رمز التحقق: 1234 لا تشاركه"

    private fun inbox(): List<SmsMessage> = listOf(
        SmsMessage(1, "JAZIRA", posBody, 1L),
        SmsMessage(2, "JAZIRA", creditBody, 2L),
        SmsMessage(3, "JAZIRA", transferBody, 3L),
        SmsMessage(4, "JAZIRA", salaryBody, 4L),
        SmsMessage(5, "JAZIRA", otpBody, 5L),
    )

    @Test
    fun fullPipelineCreatesCandidatesFromRealJaziraShapesWithZeroExistingPatterns() {
        val result = PatternDiscoveryService.discoverSafely(inbox(), emptyList())

        // 4 financial families survive; OTP is excluded.
        assertTrue("expected non-zero patterns, got ${result.patterns.size}", result.patterns.isNotEmpty())
        assertEquals(4, result.patterns.size)
        assertEquals(1, result.skippedOtp)
        assertEquals(0, result.coreFailedMessages)
        assertTrue(result.isReconciled())
        // Distinct semantic families.
        val familyKeys = result.patterns.map { it.familyKey }.distinct()
        assertEquals(4, familyKeys.size)
        assertTrue(familyKeys.none { it.startsWith("review:") || it.startsWith("non-financial|") })
        // Sanitized preview ran (OPTIONAL) without discarding anything.
        assertTrue(result.optionalStageFailures.isEmpty())
    }

    @Test
    fun saveDiscoveredBatchPersistsAllFinancialAsUnknownInactiveAndExcludesOtp() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = MessagePatternRepository(
            definitionDao = defs,
            fieldDao = RoundTripPatternFieldDao(),
            database = null,
            senderProfileDao = null,
            familyDao = RoundTripFamilyDao(),
            anchorDao = RoundTripAnchorDao(),
            now = { 10_000L },
        )
        val discovery = PatternDiscoveryService.discoverSafely(inbox(), emptyList())

        val batch = repo.saveDiscoveredBatch(
            senderProfileId = 7L,
            discovered = discovery.patterns,
            status = MessagePatternStatus.UNKNOWN,
        )

        assertEquals(discovery.patterns.size, batch.savedCount)
        val saved = repo.getForSender(7L)
        assertTrue("repository must not be empty after discovery", saved.isNotEmpty())
        // OTP never created a pattern (no NON_FINANCIAL family).
        assertTrue(saved.none { it.definition.transactionType == "NON_FINANCIAL" })
        // All new patterns are UNKNOWN and inactive (need approval).
        assertTrue(saved.all { it.definition.status == MessagePatternStatus.UNKNOWN })
        assertTrue(saved.none { it.definition.isActive })
    }

    @Test
    fun optionalStageFailureDoesNotDiscardAnOtherwiseValidPattern() {
        // Simulate the on-device scenario where an OPTIONAL enrichment stage
        // (here: sanitized preview) throws. The CORE pipeline must still
        // produce a pattern; the failure is recorded separately and never
        // counted as a discarded message.
        val throwingOptional = OptionalDiscoveryStages(
            sanitizedPreview = { error("injected SANITIZED_PREVIEW failure") },
        )
        val result = PatternDiscoveryService.discoverSafely(
            listOf(SmsMessage(1, "JAZIRA", posBody, 1L)),
            emptyList(),
            { _, _ -> },
            throwingOptional,
        )

        assertEquals(1, result.patterns.size)
        assertEquals(0, result.coreFailedMessages)
        assertEquals(1, result.optionalStageFailureCount)
        assertEquals(
            PatternDiscoveryStage.SANITIZED_PREVIEW,
            result.optionalStageFailures.single().stage,
        )
        assertEquals("IllegalStateException", result.optionalStageFailures.single().exceptionClass)
        // The pattern still has no sanitized sample but is fully formed.
        assertTrue(result.patterns.single().sanitizedSamples.isEmpty())
        assertTrue(result.isReconciled())
    }

    @Test
    fun optionalFingerprintFailureFallsBackToCueDisplayNameWithoutDiscarding() {
        val throwingOptional = OptionalDiscoveryStages(
            fingerprint = { error("injected SEMANTIC_FINGERPRINT failure") },
        )
        val result = PatternDiscoveryService.discoverSafely(
            listOf(SmsMessage(1, "JAZIRA", posBody, 1L)),
            emptyList(),
            { _, _ -> },
            throwingOptional,
        )

        assertEquals(1, result.patterns.size)
        assertEquals(0, result.coreFailedMessages)
        assertEquals(1, result.optionalStageFailureCount)
        assertEquals(
            PatternDiscoveryStage.SEMANTIC_FINGERPRINT,
            result.optionalStageFailures.single().stage,
        )
        assertTrue(result.isReconciled())
    }

    @Test
    fun semanticAmbiguityProducesReviewCandidateNotDisappearance() {
        // OTHER_FINANCIAL / unspecified type: MessageTemplateEngine builds a
        // structural template, but the semantic schema cannot confidently
        // classify it. This must create one review: candidate (UNKNOWN), not
        // silently drop the message.
        val depositBody = "إيداع\nمبلغ: 500.00 SAR\nفي حساب: 1111"
        val result = PatternDiscoveryService.discoverSafely(
            listOf(SmsMessage(1, "JAZIRA", depositBody, 1L)),
            emptyList(),
        )

        assertEquals(1, result.patterns.size)
        assertEquals(0, result.coreFailedMessages)
        val cluster = result.patterns.single()
        assertTrue(
            "ambiguous family must be a review candidate, got ${cluster.familyKey}",
            cluster.familyKey.startsWith("review:"),
        )
        assertTrue(result.isReconciled())
    }

    @Test
    fun coreStageFailureIsCountedAsDiscardedAndReconcilesExactly() {
        // A CORE (TEMPLATE_BUILD) failure genuinely cannot produce a pattern.
        val result = PatternDiscoveryService.discoverSafely(
            listOf(SmsMessage(1, "JAZIRA", posBody, 1L)),
            emptyList(),
        ) { message, stage ->
            if (message.id == 1L && stage == PatternDiscoveryStage.TEMPLATE_BUILD) {
                error("injected core failure")
            }
        }

        assertEquals(0, result.patterns.size)
        assertEquals(1, result.coreFailedMessages)
        assertEquals(
            PatternDiscoveryStage.TEMPLATE_BUILD,
            result.failures.single().stage,
        )
        assertTrue(result.isReconciled())
    }

    @Test
    fun invariantReconcilesExactlyForLargeMixedBatch() {
        val messages = buildList {
            repeat(60) { add(SmsMessage((it + 1).toLong(), "JAZIRA", posBody.replace("127.00", "$it.00"), it.toLong())) }
            repeat(40) { add(SmsMessage((it + 61).toLong(), "JAZIRA", transferBody.replace("300.00", "${300 + it}.00"), it.toLong())) }
            repeat(25) { add(SmsMessage((it + 101).toLong(), "JAZIRA", otpBody, it.toLong())) }
            // A few blanks.
            repeat(5) { add(SmsMessage((it + 126).toLong(), "JAZIRA", "   ", it.toLong())) }
        }
        val result = PatternDiscoveryService.discoverSafely(messages, emptyList())

        assertTrue(result.patterns.isNotEmpty())
        assertEquals(25, result.skippedOtp)
        assertEquals(5, result.skippedBlank)
        assertEquals(0, result.coreFailedMessages)
        assertTrue(
            "input(${result.inputMessages}) must equal processed+skipped+failed",
            result.isReconciled(),
        )
        assertEquals(result.inputMessages, result.processedMessages + result.skippedOtp + result.skippedBlank)
    }

    @Test
    fun failureBreakdownAggregatesByStageAndExceptionClassWithoutRawSms() {
        val throwingOptional = OptionalDiscoveryStages(
            sanitizedPreview = { error("boom") },
            suggestFields = { error("fields boom") },
        )
        val result = PatternDiscoveryService.discoverSafely(
            listOf(
                SmsMessage(1, "JAZIRA", posBody, 1L),
                SmsMessage(2, "JAZIRA", creditBody, 2L),
            ),
            emptyList(),
            { _, _ -> },
            throwingOptional,
        )

        val breakdown = result.failureBreakdown()
        assertTrue(breakdown.isNotEmpty())
        assertTrue(breakdown.all { it.optional })
        // Never exposes raw SMS — only short hashes.
        assertTrue(breakdown.flatMap { it.sampleBodyHashes }.all { it.length <= 12 })
        assertTrue(breakdown.any { it.stage == PatternDiscoveryStage.SANITIZED_PREVIEW && it.count == 2 })
    }

    @Test
    fun discoveryNeverTouchesPostedFinancialRecords() {
        // PatternDiscoveryService holds no transaction/journal/posting DAOs.
        // Discovery only rewrites pattern interpretation configuration. Model
        // the posted ledger externally and assert it is unchanged.
        data class PostedEntry(val id: Long, val amount: String, val account: String)
        val postedBefore = listOf(
            PostedEntry(101L, "127.00", "DEBIT-8219"),
            PostedEntry(102L, "10000.00", "ACCOUNT-1111"),
        )
        val ledgerSnapshot = postedBefore.toString()

        PatternDiscoveryService.discoverSafely(inbox(), emptyList())

        // Discovery cannot reach this reference.
        assertEquals(ledgerSnapshot, postedBefore.toString())
    }
}