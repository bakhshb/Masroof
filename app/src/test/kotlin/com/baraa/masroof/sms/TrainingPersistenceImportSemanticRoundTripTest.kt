package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.repository.MessagePatternRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPersistenceImportSemanticRoundTripTest {
    @Test
    fun rebuildWithNoUsableClustersPreservesExistingStaleApprovedPattern() = runBlocking {
        val definitions = RoundTripPatternDefinitionDao()
        val staleId = definitions.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = 1L,
                userFriendlyName = "نمط قائم",
                normalizedSignature = "stale-signature",
                canonicalKey = "stale-key",
                templateText = "شراء\nالمبلغ: {AMOUNT} SAR",
                transactionType = com.baraa.masroof.transaction.TransactionType.PURCHASE.name,
                status = MessagePatternStatus.APPROVED,
                isActive = true,
                normalizationVersion = NORMALIZATION_VERSION - 1,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val repo = MessagePatternRepository(
            definitions,
            RoundTripPatternFieldDao(),
            null,
            null,
            RoundTripFamilyDao(),
            RoundTripAnchorDao(),
        )

        val summary = repo.rebuildForSender(
            1L,
            listOf(SmsMessage(1L, "BANK", "رمز التحقق: 1234 لا تشاركه", 1L)),
        )

        val preserved = definitions.getById(staleId)!!
        assertEquals(0, summary.rebuiltVariants)
        assertEquals(0, summary.staleDeprecated)
        assertEquals(MessagePatternStatus.APPROVED, preserved.status)
        assertTrue(preserved.isActive)
    }

    @Test
    fun rebuildingDifferentFamilyDoesNotDeprecUnrepresentedStalePattern() = runBlocking {
        val definitions = RoundTripPatternDefinitionDao()
        val staleId = definitions.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = 1L,
                userFriendlyName = "شراء قائم",
                normalizedSignature = "stale-purchase",
                canonicalKey = "stale-purchase",
                templateText = "شراء\nالمبلغ: {AMOUNT} SAR",
                transactionType = com.baraa.masroof.transaction.TransactionType.PURCHASE.name,
                status = MessagePatternStatus.APPROVED,
                isActive = true,
                normalizationVersion = NORMALIZATION_VERSION - 1,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val repo = MessagePatternRepository(
            definitions,
            RoundTripPatternFieldDao(),
            null,
            null,
            RoundTripFamilyDao(),
            RoundTripAnchorDao(),
        )
        val transferOnly = SmsMessage(
            1L,
            "BANK",
            "تحويل صادر\nمن حساب: 1111\nالمبلغ: 500 SAR",
            1L,
        )

        repo.rebuildForSender(1L, listOf(transferOnly))

        val preserved = definitions.getById(staleId)!!
        assertEquals(MessagePatternStatus.APPROVED, preserved.status)
        assertTrue(preserved.isActive)
    }

    @Test
    fun rebuildLargeMixedBatchSavesValidPatternsWithoutThrowing() = runBlocking {
        val definitions = RoundTripPatternDefinitionDao()
        val repo = MessagePatternRepository(
            definitions,
            RoundTripPatternFieldDao(),
            null,
            null,
            RoundTripFamilyDao(),
            RoundTripAnchorDao(),
        )
        val messages = buildList {
            repeat(100) { index ->
                add(SmsMessage(index + 1L, "BANK", layout(index + 1), index + 1L))
            }
            repeat(25) { index ->
                add(SmsMessage(index + 101L, "BANK", "رمز التحقق: 1234 لا تشاركه", index + 101L))
            }
            repeat(25) { index ->
                add(SmsMessage(index + 126L, "BANK", "رسالة غير معروفة $index", index + 126L))
            }
        }

        val summary = repo.rebuildForSender(1L, messages)

        assertTrue(summary.rebuiltVariants > 0)
        assertEquals(25, summary.discovery?.skippedOtp)
        assertTrue(repo.getForSender(1L).isNotEmpty())
    }

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
