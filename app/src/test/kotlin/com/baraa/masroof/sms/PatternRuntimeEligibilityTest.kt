package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern
import com.baraa.masroof.data.repository.MessagePatternRepository
import com.baraa.masroof.data.repository.runtimeEligibleApprovedBySemanticKey
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.ui.senders.senderPatternFamilyStatusAr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternRuntimeEligibilityTest {
    @Test
    fun approvedActiveCurrentPatternIsRuntimeEligible() {
        assertEquals(
            PatternRuntimeEligibilityResult.ELIGIBLE,
            PatternRuntimeEligibility.evaluate(definition()),
        )
    }

    @Test
    fun approvedActiveStalePatternIsNotRuntimeEligible() {
        assertEquals(
            PatternRuntimeEligibilityResult.STALE_NORMALIZATION,
            PatternRuntimeEligibility.evaluate(
                definition().copy(normalizationVersion = NORMALIZATION_VERSION - 1),
            ),
        )
    }

    @Test
    fun otherRuntimeRejectionReasonsAreDeterministic() {
        assertEquals(
            PatternRuntimeEligibilityResult.NOT_APPROVED,
            PatternRuntimeEligibility.evaluate(
                definition().copy(status = MessagePatternStatus.UNKNOWN),
            ),
        )
        assertEquals(
            PatternRuntimeEligibilityResult.INACTIVE,
            PatternRuntimeEligibility.evaluate(definition().copy(isActive = false)),
        )
        assertEquals(
            PatternRuntimeEligibilityResult.DEPRECATED,
            PatternRuntimeEligibility.evaluate(definition().copy(deprecatedAt = 1L)),
        )
        assertEquals(
            PatternRuntimeEligibilityResult.MISSING_TEMPLATE,
            PatternRuntimeEligibility.evaluate(definition().copy(templateText = null)),
        )
        assertEquals(
            PatternRuntimeEligibilityResult.INVALID_TRANSACTION_TYPE,
            PatternRuntimeEligibility.evaluate(
                definition().copy(transactionType = "LEGACY_UNKNOWN"),
            ),
        )
    }

    @Test
    fun staleApprovedSemanticEquivalentDoesNotSuppressCandidate() {
        val stale = definition().copy(normalizationVersion = 0)
        assertTrue(runtimeEligibleApprovedBySemanticKey(listOf(stale)).isEmpty())
    }

    @Test
    fun discoveryDoesNotReportStaleApprovedAsCoverage() {
        val stale = definition().copy(normalizationVersion = 0)
        val discovered = PatternDiscoveryService.discover(
            listOf(SmsMessage(1, "JAZIRA", jaziraPos, 1L)),
            listOf(stale),
        ).single()
        assertFalse(discovered.matchedPatternStatus == MessagePatternStatus.APPROVED)
    }

    @Test
    fun staleOnlyApprovedFamilyIsPresentedAsRebuildRequired() {
        val stale = MessagePattern(
            definition = definition().copy(normalizationVersion = 0),
            fields = emptyList(),
        )
        assertEquals(
            PatternFamilyRuntimeState.APPROVED_STALE,
            patternFamilyRuntimeState(listOf(stale)),
        )
        assertEquals("نمط قديم — يحتاج إعادة بناء", senderPatternFamilyStatusAr(listOf(stale)))
    }

    @Test
    fun staleFamilyRepairsOnceThenSameMessagesResolveWithoutDuplicateFamily() = runBlocking {
        val definitions = RoundTripPatternDefinitionDao()
        val families = RoundTripFamilyDao()
        val repository = MessagePatternRepository(
            definitionDao = definitions,
            fieldDao = RoundTripPatternFieldDao(),
            database = null,
            senderProfileDao = null,
            familyDao = families,
            anchorDao = RoundTripAnchorDao(),
            now = { 10_000L },
        )
        val cluster = PatternDiscoveryService.discover(
            listOf(SmsMessage(1, "JAZIRA", jaziraPos, 1L)),
        ).single()
        val saved = repository.saveDiscovered(
            senderProfileId = 7L,
            discovered = cluster,
            status = MessagePatternStatus.APPROVED,
        )
        definitions.update(saved.definition.copy(normalizationVersion = 0))
        val familyCountBefore = families.getForSender(7L).size
        val scanMessages = listOf(
            SmsMessage(2, "JAZIRA", jaziraEquivalent, 2L),
            SmsMessage(3, "JAZIRA", jaziraEquivalent.replace("125.50", "80.00"), 3L),
        )

        val first = repository.rebuildStaleForSender(7L, scanMessages)
        assertTrue(first.rebuildAttempted)
        assertTrue(first.rebuildSucceeded)
        assertTrue(first.patternsAfterReload.any(PatternRuntimeEligibility::isEligible))
        assertTrue(
            runtimeEligibleApprovedBySemanticKey(
                first.patternsAfterReload.map { it.definition },
            ).isNotEmpty(),
        )
        assertTrue(
            first.patternsAfterReload
                .filter(PatternRuntimeEligibility::isEligible)
                .all { it.definition.normalizationVersion == NORMALIZATION_VERSION },
        )
        assertEquals(familyCountBefore, families.getForSender(7L).size)

        val outcome = TemplateResolutionService.resolve(
            sender = "JAZIRA",
            body = jaziraEquivalent,
            smsTimestampMillis = 2L,
            patterns = first.patternsAfterReload,
        )
        assertTrue(outcome is TemplateResolutionResult.Matched)
        outcome as TemplateResolutionResult.Matched
        assertTrue(
            outcome.matchTier == PatternMatchTier.EXACT_STRUCTURE ||
                outcome.matchTier == PatternMatchTier.SEMANTIC_SCHEMA,
        )
        assertNotNull(outcome.parsed.amount)

        val rowCount = first.patternsAfterReload.size
        val second = repository.rebuildStaleForSender(7L, scanMessages)
        assertFalse(second.rebuildAttempted)
        assertTrue(second.rebuildSucceeded)
        assertEquals(rowCount, second.patternsAfterReload.size)
        assertEquals(familyCountBefore, families.getForSender(7L).size)
    }

    private fun definition(): MessagePatternDefinitionEntity {
        val built = MessageTemplateEngine.buildFromSms(jaziraPos)
        return MessagePatternDefinitionEntity(
            id = 1,
            senderProfileId = 7,
            userFriendlyName = "شراء عبر نقاط البيع",
            normalizedSignature = SmsStructureNormalizer.signatureFromBody(jaziraPos),
            canonicalKey = SmsStructureNormalizer.signatureFromBody(jaziraPos),
            templateText = built.templateText,
            transactionType = TransactionType.PURCHASE.name,
            direction = built.direction,
            status = MessagePatternStatus.APPROVED,
            isActive = true,
            normalizationVersion = NORMALIZATION_VERSION,
            createdAt = 0,
            updatedAt = 0,
        )
    }

    private companion object {
        const val jaziraPos = """
            شراء عبر نقاط البيع
            بطاقة مدى رقم: 4321
            بمبلغ: 125.50 SAR
            لدى: متجر تجريبي
            في: 08:30 08-08-2026
        """
        const val jaziraEquivalent = """
            شراء عبر نقاط البيع
            التاجر: متجر آخر
            المبلغ: 125.50 SAR
            بطاقة مدى: 9876
            في: 09:45 08-08-2026
        """
    }
}
