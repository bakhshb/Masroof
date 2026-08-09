package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePatternRepository
import com.baraa.masroof.ledger.FakeSenderProfileDao
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phases 4–6 — Discovery / persist-reload-match / value-mutation (BASELINE).
 *
 * Runs the full production lifecycle on the Jazira corpus with ZERO existing
 * patterns, then approves, reloads, and re-resolves the original SMS through
 * the exact [TemplateResolutionService] used by import. Finally mutates only
 * the changing values (amount / merchant / last4 / IBAN / reference / date)
 * and asserts the approved pattern still matches.
 *
 * This is a stabilization baseline: failures are EXPECTED and recorded in the
 * Phase 8 matrix. Do NOT fix until the matrix is known.
 */
class RealJaziraCorpusLifecycleTest {

    private val senderProfileId = 1L

    private fun corpusSms(): List<SmsMessage> = RealJaziraCorpus.load().mapIndexed { i, c ->
        SmsMessage(id = (i + 1).toLong(), sender = "AlJazira", body = c.body, timestamp = (i + 1).toLong() * 1000L)
    }

    private fun smsFor(id: String): SmsMessage {
        val idx = RealJaziraCorpus.load().indexOfFirst { it.id == id }
        return SmsMessage((idx + 1).toLong(), "AlJazira", RealJaziraCorpus.load()[idx].body, (idx + 1).toLong() * 1000L)
    }

    private fun repo(): MessagePatternRepository {
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
        return MessagePatternRepository(
            definitionDao = RoundTripPatternDefinitionDao(),
            fieldDao = RoundTripPatternFieldDao(),
            database = null,
            senderProfileDao = senders,
            familyDao = RoundTripFamilyDao(),
            anchorDao = RoundTripAnchorDao(),
            now = { 10_000L },
        )
    }

    private fun familyKeyOf(body: String?): String? =
        (SemanticPatternSchemaNormalizer.fromBody(body) as? SemanticSchemaResult.Safe)?.key

    // ---------------------------------------------------------------- Phase 4
    // Split into independent checks so the FULL Phase 4 matrix is captured
    // (each @Test reports its own pass/fail; one failure does not mask the rest).

    private fun discovery() = PatternDiscoveryService.discoverSafely(corpusSms(), emptyList())

    @Test
    fun phase4a_financialSmsProduceCandidates() {
        assertTrue("discovery produced no patterns", discovery().patterns.isNotEmpty())
    }

    @Test
    fun phase4b_noValidFinancialCaseDisappears() {
        val sms = corpusSms()
        val result = discovery()
        for (message in sms) {
            // Mirror the production [TemplateResolutionService.resolve] tier
            // order: signature match first (handles compact-inline bodies where
            // strict line-by-line matching disagrees with the resolver),
            // then strict template matching.
            val runtimeSignature = SmsStructureNormalizer.signatureFromBody(message.body)
            val matched = result.patterns.any { p ->
                p.exactVariants.any { v ->
                    val sig = v.signature.substringBefore("#revision:")
                    sig.isNotBlank() && sig == runtimeSignature
                } ||
                    MessageTemplateEngine.matches(p.templateText, message.body) ||
                    p.exactVariants.any { v -> MessageTemplateEngine.matches(v.templateText, message.body) }
            }
            assertTrue("case sms id=${message.id} disappeared from discovery", matched)
        }
    }

    @Test
    fun phase4c_cases6and8SameSemanticFamily() {
        val key6 = familyKeyOf(smsFor("case6_outgoing_external_transfer").body)
        val key8 = familyKeyOf(smsFor("case8_outgoing_external_transfer_variant").body)
        assertNotNull("case6 semantic key null", key6)
        assertEquals("cases 6 & 8 must share a semantic family", key6 ?: "<none>", key8 ?: "<none>")
    }

    @Test
    fun phase4d_salaryIsSalaryFamily() {
        val salaryPattern = discovery().patterns.firstOrNull {
            TransactionTypeTaxonomy.parse(it.transactionTypeName) == TransactionType.SALARY
        }
        assertNotNull(
            "expected a SALARY family, got types=${discovery().patterns.map { it.transactionTypeName }}",
            salaryPattern,
        )
    }

    @Test
    fun phase4e_posAndOnlinePurchaseAreDifferentFamilies() {
        val key4 = familyKeyOf(smsFor("case4_english_inline_online_credit_card_purchase").body)
        val key7 = familyKeyOf(smsFor("case7_pos_samsung_pay_credit_card_purchase").body)
        assertNotEquals("case4 (online) and case7 (POS) must be different families", key4 ?: "<none>", key7 ?: "<none>")
    }

    @Test
    fun phase4f_balanceOrDueDoesNotSplitFamily() {
        val case7NoBalance = "شراء عبر نقاط البيع (Samsung Pay)\n" +
            "بطاقة ائتمانية: 7271\nلدى: TEST_SHOP_C\nبمبلغ: 178.02 SAR\nفي: 09:08 30-07-2026"
        assertEquals(
            "balance/due presence must not split the family",
            familyKeyOf(smsFor("case7_pos_samsung_pay_credit_card_purchase").body) ?: "<none>",
            familyKeyOf(case7NoBalance) ?: "<none>",
        )
    }

    @Test
    fun phase4_persistAndReload_keepsRepositoryNonEmpty() = runBlocking {
        val sms = corpusSms()
        val repo = repo()
        val result = PatternDiscoveryService.discoverSafely(sms, emptyList())
        repo.saveDiscoveredBatch(
            senderProfileId = senderProfileId,
            discovered = result.patterns,
            status = MessagePatternStatus.UNKNOWN,
        )
        assertTrue("repository empty after persist+reload", repo.getForSender(senderProfileId).isNotEmpty())
    }

    // ---------------------------------------------------------------- Phase 5

    @Test
    fun phase5_discoverSaveApproveReload_matchOriginalCorpusSms() = runBlocking {
        val sms = corpusSms()
        val repo = repo()
        val result = PatternDiscoveryService.discoverSafely(sms, emptyList())
        repo.saveDiscoveredBatch(
            senderProfileId = senderProfileId,
            discovered = result.patterns,
            status = MessagePatternStatus.UNKNOWN,
        )

        // Approve one representative per family.
        val families = repo.getForSender(senderProfileId)
            .groupBy { it.family?.id ?: -it.definition.id }
        for (rows in families.values) {
            val head = rows.maxBy { it.definition.version }
            repo.approveCandidate(head.definition.id)
        }

        // Reload from repository (the exact production resolver path).
        val approved = repo.getForSender(senderProfileId)
        assertTrue("no approved patterns after reload", approved.any { it.definition.isActive })

        // Each original corpus SMS must resolve as Matched after persistence.
        for (message in sms) {
            val resolution = TemplateResolutionService.resolve(
                sender = message.sender,
                body = message.body,
                smsTimestampMillis = message.timestamp,
                patterns = approved,
            )
            assertTrue(
                "round-trip match failed for corpus id=${message.id}: $resolution",
                resolution is TemplateResolutionResult.Matched,
            )
        }
    }

    // ---------------------------------------------------------------- Phase 6

    @Test
    fun phase6_valueMutation_stillMatchesApprovedPattern() = runBlocking {
        val sms = corpusSms()
        val repo = repo()
        val result = PatternDiscoveryService.discoverSafely(sms, emptyList())
        repo.saveDiscoveredBatch(senderProfileId, result.patterns, MessagePatternStatus.UNKNOWN)
        repo.getForSender(senderProfileId)
            .groupBy { it.family?.id ?: -it.definition.id }
            .values
            .forEach { rows -> repo.approveCandidate(rows.maxBy { it.definition.version }.definition.id) }
        val approved = repo.getForSender(senderProfileId)

        // Variants where ONLY values change (amount / merchant / beneficiary /
        // account last4 / card last4 / IBAN last4 / reference / date-time) must
        // still match the approved pattern of their family.
        val variants = listOf(
            "case1_internal_outgoing_transfer" to smsFor("case1_internal_outgoing_transfer").body!!
                .replace("4,445.67", "9,999.00").replace("3001", "4444").replace("3003", "5555"),
            "case6_outgoing_external_transfer" to smsFor("case6_outgoing_external_transfer").body!!
                .replace("13,258.00", "7,777.00").replace("3002", "4444")
                .replace("0593", "1212").replace("TEST_REFERENCE_1", "TEST_REFERENCE_X"),
            "case7_pos_samsung_pay_credit_card_purchase" to smsFor("case7_pos_samsung_pay_credit_card_purchase").body!!
                .replace("178.02", "22.00").replace("7271", "8888").replace("TEST_SHOP_C", "TEST_SHOP_D"),
            "case9_salary" to smsFor("case9_salary").body!!
                .replace("3,191.68", "5,555.00").replace("3001", "4444"),
        )
        for ((id, variantBody) in variants) {
            val resolution = TemplateResolutionService.resolve(
                sender = "AlJazira",
                body = variantBody,
                smsTimestampMillis = 99_000L,
                patterns = approved,
            )
            assertTrue(
                "phase6 mutation did not match for $id: $resolution",
                resolution is TemplateResolutionResult.Matched,
            )
        }
    }
}