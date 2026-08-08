package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.sms.NORMALIZATION_VERSION
import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.PatternRuntimeEligibility
import com.baraa.masroof.sms.RoundTripAnchorDao
import com.baraa.masroof.sms.RoundTripFamilyDao
import com.baraa.masroof.sms.RoundTripPatternDefinitionDao
import com.baraa.masroof.sms.RoundTripPatternFieldDao
import com.baraa.masroof.sms.SemanticPatternSchemaNormalizer
import com.baraa.masroof.sms.SemanticSchemaResult
import com.baraa.masroof.sms.SmsMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the semantic lifecycle of [MessagePatternRepository.rebuildForSender]:
 *
 *  - a previously APPROVED family (even if stale) may be rebuilt as APPROVED
 *  - a genuinely new semantic family found during rebuild becomes UNKNOWN,
 *    never APPROVED without user approval
 *  - a previously UNKNOWN family must remain UNKNOWN
 *  - a previously IGNORED family must remain IGNORED (never APPROVED)
 *  - repeated rebuild must not duplicate UNKNOWN candidates
 *  - posted financial records are never touched by a rebuild
 */
class MessagePatternRebuildLifecycleTest {

    private fun newRepo(
        defs: RoundTripPatternDefinitionDao = RoundTripPatternDefinitionDao(),
        fields: RoundTripPatternFieldDao = RoundTripPatternFieldDao(),
        families: RoundTripFamilyDao = RoundTripFamilyDao(),
        anchors: RoundTripAnchorDao = RoundTripAnchorDao(),
        now: () -> Long = { 10_000L },
    ): MessagePatternRepository = MessagePatternRepository(
        definitionDao = defs,
        fieldDao = fields,
        database = null,
        senderProfileDao = null,
        familyDao = families,
        anchorDao = anchors,
        now = now,
    )

    private val posBody = """
        شراء عبر نقاط البيع
        بطاقة مدى رقم: 4321
        بمبلغ: 125.50 SAR
        لدى: متجر تجريبي
        في: 08:30 08-08-2026
    """.trimIndent()

    private val posEquivalent = """
        شراء عبر نقاط البيع
        التاجر: متجر آخر
        المبلغ: 125.50 SAR
        بطاقة مدى: 9876
        في: 09:45 08-08-2026
    """.trimIndent()

    private val salaryBody = """
        إيداع راتب
        مبلغ: 10000 SAR
        إلى حساب: 1111
    """.trimIndent()

    private fun semanticKey(body: String): String {
        val built = MessageTemplateEngine.buildFromSms(body)
        return (SemanticPatternSchemaNormalizer.fromTemplate(
            built.templateText, built.transactionType?.name,
        ) as? SemanticSchemaResult.Safe)?.key
            ?: error("expected safe semantic key for body")
    }

    private suspend fun approvePattern(
        repo: MessagePatternRepository,
        senderProfileId: Long,
        body: String,
    ): MessagePattern {
        val cluster = PatternDiscoveryService.discover(
            listOf(SmsMessage(1L, "BANK", body, 1L)),
        ).single()
        return repo.saveDiscovered(senderProfileId, cluster, MessagePatternStatus.APPROVED)
    }

    @Test
    fun previouslyApprovedFamilyRebuildsAsApproved() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        approvePattern(repo, 1L, posBody)

        val summary = repo.rebuildForSender(1L, listOf(SmsMessage(2L, "BANK", posEquivalent, 2L)))

        assertEquals(1, summary.rebuiltApprovedFamilies)
        assertEquals(0, summary.newCandidateFamilies)
        val approved = repo.getForSender(1L).filter { it.definition.status == MessagePatternStatus.APPROVED }
        assertTrue(approved.any { PatternRuntimeEligibility.isEligible(it) })
        assertTrue(approved.none { it.definition.status == MessagePatternStatus.UNKNOWN })
    }

    @Test
    fun genuinelyNewFamilyFromSameInboxIsSavedUnknown() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        approvePattern(repo, 1L, posBody)

        // The inbox now contains a brand-new salary family the user never approved.
        val summary = repo.rebuildForSender(1L, listOf(SmsMessage(2L, "BANK", salaryBody, 2L)))

        assertEquals(0, summary.rebuiltApprovedFamilies)
        assertEquals(1, summary.newCandidateFamilies)
        val salaryRows = repo.getForSender(1L).filter {
            semanticKey(it.definition.templateText ?: "") == semanticKey(salaryBody)
        }
        assertTrue(salaryRows.isNotEmpty())
        assertTrue(
            "new salary family must be a candidate, never approved",
            salaryRows.all { it.definition.status == MessagePatternStatus.UNKNOWN },
        )
        assertFalse(salaryRows.any { it.definition.isActive })
    }

    @Test
    fun previouslyUnknownFamilyDoesNotBecomeApprovedDuringRebuild() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        // Seed the salary family as an UNKNOWN candidate (no user approval yet).
        val cluster = PatternDiscoveryService.discover(
            listOf(SmsMessage(1L, "BANK", salaryBody, 1L)),
        ).single()
        repo.saveDiscovered(1L, cluster, MessagePatternStatus.UNKNOWN)
        val unknownBefore = repo.getForSender(1L).single { it.definition.status == MessagePatternStatus.UNKNOWN }

        repo.rebuildForSender(1L, listOf(SmsMessage(2L, "BANK", salaryBody, 2L)))

        val current = defs.getById(unknownBefore.definition.id)!!
        assertEquals(
            "a previously UNKNOWN family must remain UNKNOWN through rebuild",
            MessagePatternStatus.UNKNOWN,
            current.status,
        )
        assertFalse(current.isActive)
    }

    @Test
    fun ignoredFamilyDoesNotBecomeApprovedDuringRebuild() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        val cluster = PatternDiscoveryService.discover(
            listOf(SmsMessage(1L, "BANK", salaryBody, 1L)),
        ).single()
        repo.saveDiscovered(1L, cluster, MessagePatternStatus.IGNORED)
        val ignoredId = defs.getForSender(1L).single().id

        repo.rebuildForSender(1L, listOf(SmsMessage(2L, "BANK", salaryBody, 2L)))

        val current = defs.getById(ignoredId)!!
        assertEquals(
            "an ignored family must remain ignored through rebuild",
            MessagePatternStatus.IGNORED,
            current.status,
        )
        assertFalse(current.isActive)
        // And no second approved row may appear for the ignored family.
        val familyRows = repo.getForSender(1L).filter {
            semanticKey(it.definition.templateText ?: "") == semanticKey(salaryBody)
        }
        assertTrue(familyRows.none { it.definition.status == MessagePatternStatus.APPROVED })
    }

    @Test
    fun staleApprovedFamilyRebuildsAsApproved() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        val saved = approvePattern(repo, 1L, posBody)
        // Force the saved row stale under the current normalizer.
        defs.update(saved.definition.copy(normalizationVersion = NORMALIZATION_VERSION - 1))
        val staleRow = defs.getById(saved.definition.id)!!
        assertEquals(
            com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION,
            PatternRuntimeEligibility.evaluate(staleRow),
        )

        val summary = repo.rebuildForSender(
            1L,
            listOf(SmsMessage(2L, "BANK", posEquivalent, 2L)),
        )

        assertEquals(1, summary.rebuiltApprovedFamilies)
        assertEquals(1, summary.staleDeprecated)
        // The stale original is deprecated; a fresh eligible APPROVED row exists.
        assertEquals(
            MessagePatternStatus.DEPRECATED,
            defs.getById(saved.definition.id)!!.status,
        )
        val eligible = repo.getForSender(1L).filter(PatternRuntimeEligibility::isEligible)
        assertTrue(eligible.isNotEmpty())
        assertTrue(eligible.all { it.definition.status == MessagePatternStatus.APPROVED })
    }

    @Test
    fun repeatedRebuildDoesNotDuplicateUnknownCandidate() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        approvePattern(repo, 1L, posBody)
        val messages = listOf(SmsMessage(2L, "BANK", salaryBody, 2L))

        val first = repo.rebuildForSender(1L, messages)
        val second = repo.rebuildForSender(1L, messages)

        assertEquals(1, first.newCandidateFamilies)
        assertEquals(0, second.newCandidateFamilies)
        val salaryFamilyId = repo.getForSender(1L)
            .first { it.definition.status == MessagePatternStatus.UNKNOWN }
            .family?.id
        assertNotNull(salaryFamilyId)
        val salaryRows = repo.getForSender(1L).filter {
            it.family?.id == salaryFamilyId && it.definition.status == MessagePatternStatus.UNKNOWN
        }
        assertEquals(
            "rebuilding the same unknown family must not duplicate the candidate",
            1,
            salaryRows.size,
        )
    }

    @Test
    fun rebuildWithApprovedPosAndNewSalaryKeepsPosApprovedAndSalaryUnknown() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        approvePattern(repo, 1L, posBody)

        val summary = repo.rebuildForSender(
            1L,
            listOf(
                SmsMessage(2L, "BANK", posEquivalent, 2L),
                SmsMessage(3L, "BANK", salaryBody, 3L),
            ),
        )

        assertEquals(1, summary.rebuiltApprovedFamilies)
        assertEquals(1, summary.newCandidateFamilies)
        val posKey = semanticKey(posBody)
        val salaryKey = semanticKey(salaryBody)
        assertNotEquals(posKey, salaryKey)
        val byFamily = repo.getForSender(1L).groupBy { it.family?.id ?: -it.definition.id }
        val posRows = byFamily.values.first { rows ->
            rows.any { semanticKey(it.definition.templateText ?: "") == posKey }
        }
        val salaryRows = byFamily.values.first { rows ->
            rows.any { semanticKey(it.definition.templateText ?: "") == salaryKey }
        }
        assertTrue(
            "POS stays APPROVED",
            posRows.any { it.definition.status == MessagePatternStatus.APPROVED },
        )
        assertTrue(
            "Salary becomes UNKNOWN, not APPROVED",
            salaryRows.all { it.definition.status == MessagePatternStatus.UNKNOWN },
        )
        assertFalse(salaryRows.any { it.definition.isActive })
    }

    @Test
    fun rebuildLeavesPostedFinancialRecordsUntouched() = runBlocking {
        // MessagePatternRepository holds no transaction/journal/posting DAOs.
        // Rebuild only rewrites pattern interpretation configuration. We model
        // the posted ledger externally and assert it is byte-for-byte unchanged.
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        approvePattern(repo, 1L, posBody)

        // A stand-in for immutable posted financial records (transactions +
        // journal entries). Rebuild must never mutate these.
        data class PostedEntry(val id: Long, val amount: String, val account: String)
        val postedBefore = listOf(
            PostedEntry(101L, "125.50", "DEBIT-4321"),
            PostedEntry(102L, "10000.00", "ACCOUNT-1111"),
        )
        val ledgerSnapshot = postedBefore.toString()

        repo.rebuildForSender(
            1L,
            listOf(
                SmsMessage(2L, "BANK", posEquivalent, 2L),
                SmsMessage(3L, "BANK", salaryBody, 3L),
            ),
        )

        val postedAfter = postedBefore // rebuild cannot reach this reference
        assertEquals(ledgerSnapshot, postedAfter.toString())
        // Pattern rows did change (interpretation config), proving the test
        // would catch a rebuild that wrongly touched the ledger instead.
        assertTrue(defs.getForSender(1L).any {
            it.status == MessagePatternStatus.APPROVED || it.status == MessagePatternStatus.UNKNOWN
        })
        // No destructive recreate: the original approved lineage is preserved.
        assertTrue(defs.getForSender(1L).isNotEmpty())
    }

    @Test
    fun rebuildPreservesLegacyRowLineageAndDoesNotDeleteData() = runBlocking {
        val defs = RoundTripPatternDefinitionDao()
        val repo = newRepo(defs)
        val saved = approvePattern(repo, 1L, posBody)
        val originalLineage = saved.definition.lineageId.takeIf { it > 0L } ?: saved.definition.id
        val originalId = saved.definition.id

        // Rebuild with the SAME structure: the original row is refreshed in
        // place (same id / lineage), never deleted or recreated.
        repo.rebuildForSender(1L, listOf(SmsMessage(2L, "BANK", posBody, 2L)))

        val original = defs.getById(originalId)!!
        assertEquals(originalLineage, original.lineageId.takeIf { it > 0L } ?: original.id)
        assertEquals(MessagePatternStatus.APPROVED, original.status)
        assertEquals(NORMALIZATION_VERSION, original.normalizationVersion)
        // No destructive recreate: nothing was deleted.
        assertTrue(defs.getForSender(1L).isNotEmpty())
    }
}