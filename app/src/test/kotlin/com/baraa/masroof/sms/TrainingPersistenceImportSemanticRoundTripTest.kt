package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePatternRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPersistenceImportSemanticRoundTripTest {
    @Test
    fun rebuildForSenderTwiceKeepsSameSemanticPatternAndCounts() = runBlocking {
        val repo = MessagePatternRepository(
            RoundTripPatternDefinitionDao(),
            RoundTripPatternFieldDao(),
            null,
            null,
            RoundTripFamilyDao(),
            RoundTripAnchorDao(),
            { 10_000L },
        )
        val inbox = (1..6).map {
            SmsMessage(it.toLong(), "BANK", layout(it), it.toLong())
        }
        repo.rebuildForSender(1L, inbox)
        val first = repo.getForSender(1L)
        repo.rebuildForSender(1L, inbox)
        val second = repo.getForSender(1L)

        assertEquals(first.size, second.size)
        assertEquals(
            first.mapNotNull { it.family?.id }.distinct().size,
            second.mapNotNull { it.family?.id }.distinct().size,
        )
        assertEquals(
            first.sumOf { it.definition.exampleCount },
            second.sumOf { it.definition.exampleCount },
        )
    }

    @Test
    fun trainApproveReloadRescanAndImportEquivalentLayoutsWithoutDuplicates() = runBlocking {
        val repo = MessagePatternRepository(
            definitionDao = RoundTripPatternDefinitionDao(),
            fieldDao = RoundTripPatternFieldDao(),
            database = null,
            senderProfileDao = null,
            familyDao = RoundTripFamilyDao(),
            anchorDao = RoundTripAnchorDao(),
            now = { 10_000L },
        )
        val inbox = (1..66).map { index ->
            SmsMessage(
                id = index.toLong(),
                sender = "BANK",
                body = layout(index).replace("STORE", "STORE $index"),
                timestamp = index.toLong(),
            )
        }

        val discovered = PatternDiscoveryService.discover(inbox)
        assertEquals(1, discovered.size)
        assertEquals(66, discovered.single().messageCount)
        repo.saveDiscovered(1L, discovered.single(), MessagePatternStatus.APPROVED)

        val persisted = repo.getForSender(1L)
        assertTrue(persisted.size >= 2)
        assertEquals(1, persisted.mapNotNull { it.family?.id }.distinct().size)
        assertEquals(66, persisted.sumOf { it.definition.exampleCount })

        val changed = """
            شراء عبر نقاط البيع
            بطاقة مدى: 9876
            في: 21:45 09-08-2026
            المبلغ: 432.10 SAR
            التاجر: NEW STORE
        """.trimIndent()
        val beforeReload = TemplateResolutionService.resolve("BANK", changed, 2L, persisted)
        val reloaded = repo.getForSender(1L)
        val afterReload = TemplateResolutionService.resolve("BANK", changed, 2L, reloaded)
        listOf(beforeReload, afterReload).forEach { outcome ->
            assertTrue(outcome is TemplateResolutionResult.Matched)
            outcome as TemplateResolutionResult.Matched
            assertEquals(PatternMatchTier.SEMANTIC_SCHEMA, outcome.matchTier)
            assertEquals("432.10", outcome.parsed.amount?.toPlainString())
            assertEquals("9876", outcome.parsed.accountOrCardLastFourDigits)
        }

        val covered = repo.ensureUnknown(
            senderProfileId = 1L,
            signature = SmsStructureNormalizer.signatureFromBody(changed),
            friendlyName = "شراء عبر نقاط البيع",
            templateText = MessageTemplateEngine.buildFromSms(changed).templateText,
            body = changed,
        )
        assertEquals(MessagePatternStatus.APPROVED, covered.definition.status)
        assertTrue(repo.getForSender(1L).none { it.definition.status == MessagePatternStatus.UNKNOWN })

        val unseen = "سحب نقدي\nبطاقة مدى: 2222\nالمبلغ: 50 SAR"
        repeat(3) {
            repo.ensureUnknown(
                1L,
                SmsStructureNormalizer.signatureFromBody(unseen),
                "سحب نقدي",
                MessageTemplateEngine.buildFromSms(unseen).templateText,
                unseen,
            )
        }
        val unknowns = repo.getForSender(1L).filter {
            it.definition.status == MessagePatternStatus.UNKNOWN
        }
        assertEquals(1, unknowns.size)
        assertNotNull(unknowns.single().family)
    }

    private fun layout(index: Int): String = when (index % 3) {
        0 -> """
            شراء عبر نقاط البيع
            لدى: STORE
            بمبلغ: ${index}.00 SAR
            في: 08:30 08-08-2026
            بطاقة مدى رقم: 1234
        """.trimIndent()
        1 -> """
            شراء عبر نقاط البيع
            التاجر: STORE
            المبلغ: ${index}.00 SAR
            بطاقة مدى: 1234
            في: 08:30 08-08-2026
        """.trimIndent()
        else -> """
            شراء عبر نقاط البيع
            بطاقة مدى رقم: 1234
            في: 08:30 08-08-2026
            مبلغ العملية: ${index}.00 SAR
            Merchant: STORE
            الرصيد المتاح: 9999 SAR
        """.trimIndent()
    }
}
