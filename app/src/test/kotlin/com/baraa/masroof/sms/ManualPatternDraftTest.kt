package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePatternRepository
import com.baraa.masroof.ledger.FakeSenderProfileDao
import com.baraa.masroof.sms.RoundTripAnchorDao
import com.baraa.masroof.sms.RoundTripFamilyDao
import com.baraa.masroof.sms.RoundTripPatternDefinitionDao
import com.baraa.masroof.sms.RoundTripPatternFieldDao
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the exact physical-device bug: tapping a message in the
 * manual picker silently did nothing because [PatternDraftFactory] did not
 * exist and the flow re-ran the whole batch discovery pipeline.
 *
 * These tests cover the shared single-SMS draft factory and the "no Room row
 * before confirm / exactly one APPROVED row after Save & Approve / resolver
 * matches an equivalent SMS" contract.
 */
class ManualPatternDraftTest {

    private val posBody = """
        شراء عبر نقاط البيع (Google Pay)
        لدى: MALAYSIA FOODS RESTA
        بمبلغ: 127.00 SAR
        في: 13:24 2026-08-03
        بطاقة مدى رقم: 8219
    """.trimIndent()

    private val equivalentPosBody = """
        شراء عبر نقاط البيع (Google Pay)
        لدى: ANOTHER STORE LLC
        بمبلغ: 64.50 SAR
        في: 09:01 2026-08-09
        بطاقة مدى رقم: 5555
    """.trimIndent()

    private fun repo(): Triple<
        MessagePatternRepository,
        FakeSenderProfileDao,
        RoundTripPatternDefinitionDao,
        > {
        val senders = FakeSenderProfileDao()
        runBlocking {
            senders.insert(
                com.baraa.masroof.data.db.SenderProfileEntity(
                    id = 0L,
                    displaySender = "AlJazira",
                    normalizedSenderKey = "aljazira",
                    active = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
        }
        val defs = RoundTripPatternDefinitionDao()
        val repo = MessagePatternRepository(
            definitionDao = defs,
            fieldDao = RoundTripPatternFieldDao(),
            database = null,
            senderProfileDao = senders,
            familyDao = RoundTripFamilyDao(),
            anchorDao = RoundTripAnchorDao(),
            now = { 10_000L },
        )
        return Triple(repo, senders, defs)
    }

    @Test
    fun fromSms_posMessage_isReadyWithKnownTypeAndTemplate() {
        val result = PatternDraftFactory.fromSms(
            SmsMessage(1, "AlJazira", posBody, 1L),
            senderProfileId = 1L,
        )
        assertTrue("expected Ready, got $result", result is PatternDraftResult.Ready)
        val draft = (result as PatternDraftResult.Ready).draft
        assertEquals(0L, draft.templateEditDraft.patternId)
        assertEquals(1L, draft.templateEditDraft.senderProfileId)
        assertEquals(TransactionType.PURCHASE, draft.templateEditDraft.transactionType)
        assertTrue(draft.templateEditDraft.templateText.contains("{AMOUNT}"))
        assertTrue(draft.templateEditDraft.templateText.contains("{MERCHANT}"))
        assertTrue(draft.templateEditDraft.templateText.contains("{DEBIT_CARD_LAST4}"))
        // Amount field is required; a money field exists.
        assertTrue(draft.templateEditDraft.fields.any {
            it.canonicalField == com.baraa.masroof.data.db.PatternCanonicalField.TRANSACTION_AMOUNT
        })
        // Sanitized example is present and never the raw SMS body.
        assertTrue(draft.sanitizedExample.isNotBlank())
        assertFalse(draft.sanitizedExample == posBody)
    }

    @Test
    fun fromSms_uncertainType_isNeedsTypeSelectionNotFailure() {
        val deposit = "إيداع\nمبلغ: 500.00 SAR\nفي حساب: 1111"
        val result = PatternDraftFactory.fromSms(SmsMessage(1, "BANK", deposit, 1L), 1L)
        assertTrue("expected NeedsTypeSelection, got $result", result is PatternDraftResult.NeedsTypeSelection)
        val draft = (result as PatternDraftResult.NeedsTypeSelection).draft
        // Template + fields are still visible so the editor can present them.
        assertTrue(draft.templateEditDraft.templateText.contains("{AMOUNT}"))
        assertTrue(draft.templateEditDraft.fields.isNotEmpty())
    }

    @Test
    fun fromSms_noRoomRowExistsBeforeUserConfirms() = runBlocking {
        val (repo, _, _) = repo()
        val result = PatternDraftFactory.fromSms(SmsMessage(1, "AlJazira", posBody, 1L), 1L)
        assertTrue(result is PatternDraftResult.Ready)
        // Building the draft must NOT persist anything.
        assertTrue(repo.getForSender(1L).isEmpty())
    }

    @Test
    fun saveAndApprove_persistsExactlyOneApprovedFamilyWithFieldsAndAnchors() = runBlocking {
        val (repo, _, _) = repo()
        val draft = (PatternDraftFactory.fromSms(
            SmsMessage(1, "AlJazira", posBody, 1L), 1L,
        ) as PatternDraftResult.Ready).draft

        val saveResult = repo.createPatternFromDraft(draft.templateEditDraft, approve = true)
        assertTrue("save failed: $saveResult", saveResult is MessagePatternRepository.TemplateUpdateResult.Success)

        val saved = repo.getForSender(1L)
        assertTrue("repository must not be empty after Save & Approve", saved.isNotEmpty())
        // Exactly one APPROVED, active variant at the current normalization version.
        val approved = saved.filter { it.definition.status == MessagePatternStatus.APPROVED }
        assertEquals(1, approved.size)
        assertTrue(approved.single().definition.isActive)
        assertEquals(NORMALIZATION_VERSION, approved.single().definition.normalizationVersion)
        assertTrue(approved.single().fields.isNotEmpty())
        assertTrue(approved.single().anchors.isNotEmpty())
        // No UNKNOWN left behind (no UNKNOWN-then-revision double write).
        assertTrue(saved.none { it.definition.status == MessagePatternStatus.UNKNOWN })
        // Counters as the SenderDetails screen computes them.
        val approvedFamilies = saved.filter {
            com.baraa.masroof.data.repository.TemplateStatusLabels.isApprovedTemplate(it.definition.status)
        }.groupBy { it.family?.id ?: -it.definition.id }
        assertEquals(1, approvedFamilies.size)
        assertTrue(saved.sumOf { it.definition.exampleCount } > 0)
    }

    @Test
    fun saveAsCandidate_persistsOneUnknownInactive() = runBlocking {
        val (repo, _, _) = repo()
        val draft = (PatternDraftFactory.fromSms(
            SmsMessage(1, "AlJazira", posBody, 1L), 1L,
        ) as PatternDraftResult.Ready).draft
        val saveResult = repo.createPatternFromDraft(draft.templateEditDraft, approve = false)
        assertTrue(saveResult is MessagePatternRepository.TemplateUpdateResult.Success)
        val saved = repo.getForSender(1L)
        assertEquals(1, saved.size)
        assertEquals(MessagePatternStatus.UNKNOWN, saved.single().definition.status)
        assertFalse(saved.single().definition.isActive)
    }

    @Test
    fun approvedManualPattern_resolvesEquivalentSmsAsMatched() = runBlocking {
        val (repo, _, _) = repo()
        val draft = (PatternDraftFactory.fromSms(
            SmsMessage(1, "AlJazira", posBody, 1L), 1L,
        ) as PatternDraftResult.Ready).draft
        repo.createPatternFromDraft(draft.templateEditDraft, approve = true)

        val patterns = repo.getForSender(1L)
        val resolution = TemplateResolutionService.resolve(
            sender = "AlJazira",
            body = equivalentPosBody,
            smsTimestampMillis = 2L,
            patterns = patterns,
        )
        assertTrue(
            "equivalent POS SMS must resolve to Matched, got $resolution",
            resolution is TemplateResolutionResult.Matched,
        )
    }

    @Test
    fun createPatternFromDraft_rejectsDraftWithExistingPatternId() = runBlocking {
        val (repo, _, _) = repo()
        val draft = (PatternDraftFactory.fromSms(
            SmsMessage(1, "AlJazira", posBody, 1L), 1L,
        ) as PatternDraftResult.Ready).draft
        val badDraft = draft.templateEditDraft.copy(patternId = 999L)
        val result = repo.createPatternFromDraft(badDraft, approve = true)
        assertTrue(result is MessagePatternRepository.TemplateUpdateResult.ValidationError)
        assertTrue(repo.getForSender(1L).isEmpty())
    }
}