package com.baraa.masroof.bank.aljazira

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardMessageHeuristicsTest {
    @Test
    fun creditCardPurchase_isDetected() {
        val body = """
            شراء عبر الانترنت (Samsung Pay)
            بطاقة ائتمانية: 7271
            لدى: ananinja.com
            بمبلغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent()
        assertTrue(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun englishCreditCard_isDetected() {
        val body =
            "Online Purchase Apple Pay Credit Card: 3478 at :Tamara of : 53.38 SAR " +
                "Available Balance is: 14644.09 SAR Due Amount: 3921.11 SAR"
        assertTrue(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun madaPurchase_isRejected() {
        val body = """
            شراء من نقاط البيع
            بطاقة مدى: 2210
            لدى: TEST_GROCER
            بمبلغ: 120.00 SAR
            خصمت من حساب: 3001
        """.trimIndent()
        assertFalse(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun transfer_isRejected() {
        val body = """
            حوالة واردة: محلية
            مبلغ: SAR 100.00
            إلى: 3001
        """.trimIndent()
        assertFalse(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun madaPurchase_isDebitCardSms() {
        val body = """
            شراء من نقاط البيع
            بطاقة مدى: 2210
            لدى: TEST_GROCER
            بمبلغ: 120.00 SAR
            خصمت من حساب: 3001
        """.trimIndent()
        assertTrue(CreditCardMessageHeuristics.isDebitCardSms(body))
    }

    @Test
    fun googlePayMada_withoutAccountDebit_isDebitCardSms() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            بطاقة مدى: 8219
            لدى: MALAYSIA FOODS RESTA
            بمبلغ: 127.00 SAR
            في: 13:24 03-08-2026
        """.trimIndent()
        assertTrue(CreditCardMessageHeuristics.isDebitCardSms(body))
        assertFalse(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun creditCardGooglePay_isNotDebitCardSms() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            بطاقة ائتمانية: 7271
            لدى: ananinja.com
            بمبلغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent()
        assertFalse(CreditCardMessageHeuristics.isDebitCardSms(body))
        assertTrue(CreditCardMessageHeuristics.isCreditCardSms(body))
    }

    @Test
    fun creditCardAtRamadanMerchant_isCredit_notDebit() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            بطاقة ائتمانية: 7271
            لدى: Ramadan Gifts
            بمبلغ: 75.00 SAR
            الرصيد المتاح: 14569.09 SAR
            إجمالي المبلغ المستحق:3921.11 SAR
        """.trimIndent()
        assertTrue(CreditCardMessageHeuristics.isCreditCardSms(body))
        assertFalse(CreditCardMessageHeuristics.isDebitCardSms(body))
    }

    @Test
    fun englishMadaCard_isDebitCardSms() {
        val body = "POS Purchase MADA: 8219 at STORE amount 50.00 SAR"
        assertTrue(CreditCardMessageHeuristics.isDebitCardSms(body))
        assertFalse(CreditCardMessageHeuristics.isCreditCardSms(body))
    }
}
