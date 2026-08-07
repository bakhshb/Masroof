package com.baraa.masroof.ai

import com.baraa.masroof.transaction.FinancialTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceLinkAssistTest {

    private val accounts = listOf(
        LinkAssistAccount(1L, "راتب", "BANK_ACCOUNT", listOf("1234")),
        LinkAssistAccount(2L, "فيزا", "CREDIT_CARD", listOf("5678")),
        LinkAssistAccount(3L, "محفظة", "DIGITAL_WALLET", listOf("9012")),
    )

    private val request = LinkAssistRequest(
        smsBody = "شراء من متجر بمبلغ 50.00 SAR بطاقة *1234",
        sender = "Bank",
        transactionType = "PURCHASE",
        amount = "50.00",
        currency = "SAR",
        transactionDate = "2026-08-01",
        lastFourEvidence = "1234",
        accounts = accounts,
    )

    @Test
    fun acceptsKeyValueLines() {
        val raw = """
            TREATMENT=EXPENSE
            ACCOUNT=1
            ACCOUNT2=
            CONF=88
            REASON=شراء من البطاقة
        """.trimIndent()
        val suggestion = OnDeviceLinkAssist.parseSuggestion(raw, request)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.EXPENSE, suggestion!!.treatment)
        assertEquals(1L, suggestion.sourceAccountId)
        assertNull(suggestion.destinationAccountId)
        assertEquals(88, suggestion.confidence)
    }

    @Test
    fun acceptsValidExpenseJson() {
        val json = """
            {"treatment":"EXPENSE","sourceAccountId":1,"destinationAccountId":null,"confidence":88,"reasonAr":"شراء من البطاقة"}
        """.trimIndent()
        val suggestion = OnDeviceLinkAssist.parseSuggestion(json, request)
        assertNotNull(suggestion)
        assertEquals(1L, suggestion!!.sourceAccountId)
    }

    @Test
    fun acceptsArabicTreatmentAndStringAccountId() {
        val raw = """
            TREATMENT=مصروف
            ACCOUNT=راتب
            CONF=75
            REASON=شراء
        """.trimIndent()
        val suggestion = OnDeviceLinkAssist.parseSuggestion(raw, request)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.EXPENSE, suggestion!!.treatment)
        assertEquals(1L, suggestion.sourceAccountId)
    }

    @Test
    fun smsBodyHeuristicWhenModelFails() {
        val suggestion = OnDeviceLinkAssist.suggestFromSmsBody(request)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.EXPENSE, suggestion!!.treatment)
        assertEquals(1L, suggestion.sourceAccountId)
        assertTrue(suggestion.reasonAr.contains("نص الرسالة"))
    }

    @Test
    fun matchesLastFourFromSmsBodyWhenParserEvidenceMissing() {
        val req = request.copy(lastFourEvidence = null)
        val suggestion = OnDeviceLinkAssist.suggestFromSmsBody(req)
        assertNotNull(suggestion)
        assertEquals(1L, suggestion!!.sourceAccountId)
    }

    @Test
    fun acceptsCreditCardPaymentWithTwoAccounts() {
        val raw = """
            TREATMENT=CREDIT_CARD_PAYMENT
            ACCOUNT=1
            ACCOUNT2=2
            CONF=91
            REASON=سداد بطاقة
        """.trimIndent()
        val suggestion = OnDeviceLinkAssist.parseSuggestion(raw, request)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.CREDIT_CARD_PAYMENT, suggestion!!.treatment)
        assertEquals(1L, suggestion.sourceAccountId)
        assertEquals(2L, suggestion.destinationAccountId)
    }

    @Test
    fun rejectsInternalTransferWithoutTwoAccounts() {
        val raw = """
            TREATMENT=INTERNAL_TRANSFER
            ACCOUNT=1
            ACCOUNT2=
            CONF=70
        """.trimIndent()
        assertNull(OnDeviceLinkAssist.parseSuggestion(raw, request))
    }

    @Test
    fun promptAsksForKeyValueNotJson() {
        val prompt = OnDeviceLinkAssist.buildPrompt(request)
        assertTrue(prompt.contains("TREATMENT=EXPENSE"))
        assertTrue(prompt.contains("ACCOUNT="))
        assertTrue(prompt.contains("SMS:"))
        assertTrue(prompt.contains("شراء من متجر"))
        assertTrue(!prompt.contains("JSON object only") || prompt.contains("no JSON"))
    }

    @Test
    fun extractsJsonFromFencedModelOutput() {
        val raw = """
            ```json
            {"treatment":"INCOME","sourceAccountId":null,"destinationAccountId":1,"confidence":80,"reasonAr":"راتب"}
            ```
        """.trimIndent()
        val suggestion = OnDeviceLinkAssist.parseSuggestion(raw, request)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.INCOME, suggestion!!.treatment)
        assertEquals(1L, suggestion.destinationAccountId)
    }
}
