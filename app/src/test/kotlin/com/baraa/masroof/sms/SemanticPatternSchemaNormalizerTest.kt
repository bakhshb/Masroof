package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticPatternSchemaNormalizerTest {
    @Test
    fun equivalentMerchantAndAmountLabelsAndOrderShareOneKey() {
        assertEquals(key(posA), key(posB))
    }

    @Test
    fun harmlessLabelPunctuationDoesNotChangeSemanticKey() {
        val punctuated = posB
            .replace("التاجر:", "التاجر -:")
            .replace("المبلغ:", "المبلغ.:")
        assertEquals(key(posB), key(punctuated))
    }

    @Test
    fun merchantSynonymsMapToMerchant() {
        listOf("لدى", "التاجر", "Merchant", "at").forEach {
            assertTrue(PatternCanonicalField.MERCHANT in CanonicalPatternFieldClassifier.classify(it))
        }
    }

    @Test
    fun amountSynonymsMapToTransactionAmount() {
        listOf("بمبلغ", "المبلغ", "مبلغ العملية", "قيمة العملية", "Amount", "Purchase Amount").forEach {
            assertTrue(
                PatternCanonicalField.TRANSACTION_AMOUNT in
                    CanonicalPatternFieldClassifier.classify(it),
            )
        }
    }

    @Test
    fun optionalBalanceDueAndReferenceDoNotChangeKey() {
        val extras = listOf(
            "\nالرصيد المتاح: 9999 SAR",
            "\nإجمالي المبلغ المستحق: 8888 SAR",
            "\nرقم المرجع: 123456789",
        )
        extras.forEach { assertEquals(key(posA), key(posA + it)) }
    }

    @Test
    fun debitAndCreditPosRemainDifferent() {
        assertNotEquals(key(posA), key(posA.replace("بطاقة مدى رقم", "بطاقة ائتمانية")))
    }

    @Test
    fun posAndOnlineRemainDifferent() {
        assertNotEquals(key(posA), key(posA.replace("نقاط البيع", "الإنترنت")))
    }

    @Test
    fun transferDirectionsRemainDifferent() {
        val incoming = "حوالة واردة\nإلى حساب: 1234\nالمبلغ: 100 SAR"
        val outgoing = "حوالة صادرة\nمن حساب: 1234\nالمبلغ: 100 SAR"
        assertNotEquals(key(incoming), key(outgoing))
    }

    @Test
    fun purchaseAndRefundRemainDifferent() {
        assertNotEquals(key(posA), key(posA.replace("شراء عبر نقاط البيع", "استرداد عملية شراء")))
    }

    @Test
    fun otpIsNeverSafeSemanticFinancialSchema() {
        assertTrue(
            SemanticPatternSchemaNormalizer.fromBody("رمز التحقق: 123456") is
                SemanticSchemaResult.NonFinancial,
        )
    }

    @Test
    fun semanticKeyIsVersionedAndStableAcrossBodyAndTemplate() {
        val built = MessageTemplateEngine.buildFromSms(posA)
        val body = SemanticPatternSchemaNormalizer.fromBody(posA) as SemanticSchemaResult.Safe
        val template = SemanticPatternSchemaNormalizer.fromTemplate(
            built.templateText,
            built.transactionType?.name,
        ) as SemanticSchemaResult.Safe
        assertEquals(body.key, template.key)
        assertTrue(body.key.startsWith("semantic-v1|"))
    }

    @Test
    fun amountExtractorNeverUsesIdentifiersReferenceBalanceOrDue() {
        val body = """
            شراء عبر نقاط البيع
            بطاقة مدى: 1234
            رقم الحساب: 5678
            آيبان: 9012
            المرجع: 777777
            الرصيد المتاح: 4444 SAR
            إجمالي المبلغ المستحق: 3333 SAR
        """.trimIndent()
        assertNull(CanonicalSmsFieldExtractor.extract(body).amount)
    }

    @Test
    fun missingAmountSemanticMatchReturnsNeedsReview() {
        val trained = approvedPattern(posA)
        val missing = posB.lines().filterNot { "المبلغ" in it }.joinToString("\n")
        val outcome = TemplateResolutionService.resolve("BANK", missing, 1L, listOf(trained))
        assertTrue(outcome is TemplateResolutionResult.Matched)
        outcome as TemplateResolutionResult.Matched
        assertNull(outcome.parsed.amount)
        assertEquals(com.baraa.masroof.transaction.TransactionStatus.NEEDS_REVIEW, outcome.parsed.status)
    }

    @Test
    fun sixtySixHarmlessPosLayoutsProduceOneSemanticPattern() {
        val messages = (1..66).map { index ->
            val body = if (index % 3 == 0) {
                posB.replace("IKEA", "STORE $index").replace("100", index.toString())
            } else {
                posA.replace("IKEA", "STORE $index").replace("100", index.toString())
            }
            SmsMessage(index.toLong(), "BANK", body, index.toLong())
        }
        val discovered = PatternDiscoveryService.discover(messages)
        assertEquals(1, discovered.size)
        assertEquals(66, discovered.single().messageCount)
        assertTrue(discovered.single().exactVariants.size >= 2)
    }

    private fun approvedPattern(body: String): com.baraa.masroof.data.repository.MessagePattern {
        val built = MessageTemplateEngine.buildFromSms(body)
        val discovered = PatternDiscoveryService.discover(
            listOf(SmsMessage(1, "BANK", body, 1L)),
        ).single()
        val definition = com.baraa.masroof.data.db.MessagePatternDefinitionEntity(
            id = 1,
            senderProfileId = 1,
            userFriendlyName = discovered.friendlyNameHint,
            normalizedSignature = built.signature,
            canonicalKey = built.signature,
            templateText = built.templateText,
            transactionType = built.transactionType?.name,
            direction = built.direction,
            status = com.baraa.masroof.data.db.MessagePatternStatus.APPROVED,
            isActive = true,
            createdAt = 0,
            updatedAt = 0,
        )
        val fields = discovered.suggestedFields.map {
            com.baraa.masroof.data.db.PatternFieldDefinitionEntity(
                patternId = 1,
                canonicalField = it.canonicalField,
                placeholderToken = TemplateResolutionService.defaultPlaceholder(it.canonicalField),
                sourceLabel = it.sourceLabel,
                required = it.required,
                role = it.role,
                valueType = it.valueType,
            )
        }
        return com.baraa.masroof.data.repository.MessagePattern(definition, fields)
    }

    private fun key(body: String): String =
        (SemanticPatternSchemaNormalizer.fromBody(body) as SemanticSchemaResult.Safe).key

    private companion object {
        const val posA = """
            شراء عبر نقاط البيع
            لدى: IKEA
            بمبلغ: 100 SAR
            في: 08:30 08-08-2026
            بطاقة مدى رقم: 1234
        """
        const val posB = """
            شراء عبر نقاط البيع
            التاجر: IKEA
            المبلغ: 100 SAR
            بطاقة مدى: 1234
            في: 08:30 08-08-2026
        """
    }
}
