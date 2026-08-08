package com.baraa.masroof.sms

import com.baraa.masroof.data.db.PatternVariantAnchorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternFamilyVariantArchitectureTest {
    @Test
    fun structurallyDifferentTransfersAreVariantsInOneFamilyNotOneTemplate() {
        val first = """
            تحويل صادر
            خصمت من حساب: 3001
            إلى: متجر ألف
            بمبلغ: 20.00 SAR
        """.trimIndent()
        val second = """
            تحويل صادر
            خصمت من حساب: 3001
            المستفيد: متجر باء
            بمبلغ: 30.00 SAR
        """.trimIndent()

        val discovered = PatternDiscoveryService.discover(
            listOf(
                SmsMessage(1, "BANK", first, 1L, MatchReason.NONE),
                SmsMessage(2, "BANK", second, 2L, MatchReason.NONE),
            ),
        )

        assertEquals(2, discovered.size)
        assertEquals(1, discovered.map { it.familyKey }.distinct().size)
        assertNotEquals(discovered[0].canonicalKey, discovered[1].canonicalKey)
    }

    @Test
    fun variableValuesShareOneVariantSignature() {
        val first = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
            في: 22:50 03-08-2026
        """.trimIndent()
        val second = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 4444
            بمبلغ: 129.50 SAR
            لدى: Amazon
            في: 13:16 08-08-2026
        """.trimIndent()
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(first),
            SmsStructureNormalizer.signatureFromBody(second),
        )
    }

    @Test
    fun optionalBalanceDoesNotCreateAnotherVariant() {
        val base = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
        """.trimIndent()
        val discovered = PatternDiscoveryService.discover(
            listOf(
                SmsMessage(1, "BANK", base, 1L, MatchReason.NONE),
                SmsMessage(2, "BANK", base + "\nالرصيد المتاح: 1200.00 SAR", 2L, MatchReason.NONE),
            ),
        )
        assertEquals(1, discovered.size)
        assertEquals(2, discovered.single().messageCount)
    }

    @Test
    fun optionalBalanceMayBeAbsentButContradictoryCardAnchorCannotMatch() {
        val template = """
            شراء عبر الانترنت
            بطاقة ائتمانية: {CREDIT_CARD_LAST4}
            بمبلغ: {AMOUNT} SAR
            لدى: {MERCHANT}
            الرصيد المتاح: {AVAILABLE_BALANCE} SAR
        """.trimIndent()
        val anchors = listOf(
            PatternVariantAnchorEntity(variantId = 1, normalizedAnchor = "شراء عبر الانترنت", required = true),
            PatternVariantAnchorEntity(variantId = 1, normalizedAnchor = "بطاقه ائتمانيه", required = true),
            PatternVariantAnchorEntity(variantId = 1, normalizedAnchor = "بمبلغ", required = true),
            PatternVariantAnchorEntity(variantId = 1, normalizedAnchor = "لدي", required = true),
            PatternVariantAnchorEntity(variantId = 1, normalizedAnchor = "الرصيد المتاح", required = false),
        )
        val withoutOptional = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
        """.trimIndent()
        val contradictory = withoutOptional.replace("بطاقة ائتمانية", "بطاقة مدى")

        assertTrue(TemplateMatcher.match(template, withoutOptional, anchors).matched)
        assertFalse(TemplateMatcher.match(template, contradictory, anchors).matched)
    }
}
