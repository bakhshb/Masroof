package com.baraa.masroof.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplateEngineTest {

    private val exampleSms = """
        شراء عبر الانترنت
        بطاقة ائتمانية: 7271
        بمبلغ :51.99 SAR
        لدى :Keeta
        في :22:50 03-08-2026
        الرصيد المتاح :SAR 17230.03
        إجمالي المبلغ المستحق:2380.88 SAR
    """.trimIndent()

    @Test
    fun buildsExactStructuralTemplate_fromSelectedSms() {
        val built = MessageTemplateEngine.buildFromSms(exampleSms)
        assertTrue(built.templateText.contains("شراء عبر الانترنت"))
        assertTrue(built.templateText.contains("{CREDIT_CARD_LAST4}"))
        assertTrue(built.templateText.contains("{AMOUNT}"))
        assertTrue(built.templateText.contains("{MERCHANT}"))
        assertTrue(built.templateText.contains("{TIME}"))
        assertTrue(built.templateText.contains("{DATE}"))
        assertTrue(built.templateText.contains("{AVAILABLE_BALANCE}"))
        assertTrue(built.templateText.contains("{TOTAL_DUE}"))
        assertFalse(built.templateText.contains("7271"))
        assertFalse(built.templateText.contains("Keeta"))
        assertFalse(built.templateText.contains("51.99"))
        assertEquals("شراء عبر الإنترنت", built.displayName.substringBefore(" ·"))
    }

    @Test
    fun amountNotConfusedWithCardLast4() {
        val built = MessageTemplateEngine.buildFromSms(exampleSms)
        assertTrue(built.placeholders.contains("CREDIT_CARD_LAST4"))
        assertTrue(built.placeholders.contains("AMOUNT"))
        val cardLine = built.templateText.lineSequence().first { it.contains("بطاقة") }
        assertTrue(cardLine.contains("{CREDIT_CARD_LAST4}"))
        assertFalse(cardLine.contains("{AMOUNT}"))
        val amountLine = built.templateText.lineSequence().first { it.contains("بمبلغ") }
        assertTrue(amountLine.contains("{AMOUNT}"))
        assertFalse(amountLine.contains("{CREDIT_CARD_LAST4}"))
    }

    @Test
    fun sameStructureDifferentValues_matches() {
        val template = MessageTemplateEngine.buildFromSms(exampleSms).templateText
        val variant = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 1234
            بمبلغ :88.00 SAR
            لدى :Amazon
            في :10:15 01-01-2026
            الرصيد المتاح :SAR 99.00
            إجمالي المبلغ المستحق:10.00 SAR
        """.trimIndent()
        assertTrue(MessageTemplateEngine.matches(template, variant))
    }

    @Test
    fun structurallyDifferent_doesNotMatch() {
        val template = MessageTemplateEngine.buildFromSms(exampleSms).templateText
        val transfer = """
            تحويل صادر
            مبلغ التحويل: 50 SAR
            إلى حساب: 9999
        """.trimIndent()
        assertFalse(MessageTemplateEngine.matches(template, transfer))
    }

    @Test
    fun discover_exposesTemplateNotOnlySamples() {
        val clusters = PatternDiscoveryService.discover(
            listOf(SmsMessage(1, "BankX", exampleSms, 1L, MatchReason.NONE)),
        )
        assertEquals(1, clusters.size)
        assertTrue(clusters.single().templateText.contains("{AMOUNT}"))
        assertTrue(clusters.single().placeholders.isNotEmpty())
    }

    @Test
    fun accountLabelMapsToAccountLast4NotDebitCard() {
        val sms = """
            عملية حوالة مالية صادرة مقبولة
            خصمت من حساب: 3001
            إلى: ولاء عاشور
            مبلغ العملية: SAR 1,789.00
            المعرف البديل الايبان: 6810
        """.trimIndent()
        val built = MessageTemplateEngine.buildFromSms(sms)
        assertTrue(built.templateText.contains("{SOURCE_ACCOUNT_LAST4}"))
        assertFalse(built.templateText.contains("{DEBIT_CARD_LAST4}"))
        assertTrue(
            built.templateText.contains("{DESTINATION_IBAN_LAST4}") ||
                built.placeholders.contains("DESTINATION_IBAN_LAST4"),
        )
    }
}
