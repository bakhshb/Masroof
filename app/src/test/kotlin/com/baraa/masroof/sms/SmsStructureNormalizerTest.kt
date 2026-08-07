package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsStructureNormalizerTest {

    @Test
    fun sameStructure_differentValues_sameSignature() {
        val a = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 51.99 SAR
            لدى: Keeta
            في: 22:50 03-08-2026
        """.trimIndent()
        val b = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ: 129.50 SAR
            لدى: Amazon
            في: 13:16 08-08-2026
        """.trimIndent()
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(a),
            SmsStructureNormalizer.signatureFromBody(b),
        )
    }

    @Test
    fun differentStructure_differentSignature() {
        val purchase = "شراء\nبمبلغ: 10 SAR\nبطاقة: 1111"
        val transfer = "تحويل\nمبلغ التحويل: 10 SAR\nإلى حساب: 2222"
        assertFalse(
            SmsStructureNormalizer.signatureFromBody(purchase) ==
                SmsStructureNormalizer.signatureFromBody(transfer),
        )
    }

    @Test
    fun discover_groupsValueVariants() {
        val messages = listOf(
            SmsMessage(1, "BankX", """
                شراء
                بمبلغ: 10.00 SAR
                لدى: StoreOne
            """.trimIndent(), 1000, MatchReason.NONE),
            SmsMessage(2, "BankX", """
                شراء
                بمبلغ: 99.50 SAR
                لدى: StoreTwo
            """.trimIndent(), 2000, MatchReason.NONE),
            SmsMessage(3, "BankX", """
                تحويل
                مبلغ التحويل: 5.00 SAR
                إلى: OtherParty
            """.trimIndent(), 3000, MatchReason.NONE),
        )
        val clusters = PatternDiscoveryService.discover(messages)
        assertTrue(clusters.isNotEmpty())
        assertEquals(
            messages.size,
            clusters.sumOf { it.messageCount },
        )
        val purchaseSig = SmsStructureNormalizer.signatureFromBody(messages[0].body)
        val purchaseSig2 = SmsStructureNormalizer.signatureFromBody(messages[1].body)
        val transferSig = SmsStructureNormalizer.signatureFromBody(messages[2].body)
        assertEquals(purchaseSig, purchaseSig2)
        assertTrue(purchaseSig != transferSig)
    }
}
