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
}
