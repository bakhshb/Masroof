package com.baraa.masroof.transaction.banks

import com.baraa.masroof.transaction.BankParserRegistry
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Anonymized SNB fixtures routed through the registry. */
class SNBParserFixtureTest {
    @Test
    fun snbMadaPurchaseUsesLabeledAmountAndDebitLast4() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            لدى: MALAYSIA FOODS RESTA
            بمبلغ: 127.00 SAR
            في: 13:24 2026-08-03
            بطاقة مدى رقم: 8219
        """.trimIndent()
        val parsed = BankParserRegistry.parse("SNB", body, 1_725_000_000_000L)
        assertEquals("SNB", parsed.parserName)
        assertEquals(0, BigDecimal("127.00").compareTo(parsed.amount))
        assertEquals("8219", parsed.accountOrCardLastFourDigits)
        assertEquals(Currency.SAR, parsed.currency)
    }

    @Test
    fun snbOutgoingTransferKeepsSourceAccountNotIbanAsAmount() {
        val body = """
            عملية حوالة مالية صادرة مقبولة
            خصمت من حساب: 3001
            الى: مستفيد
            مبلغ العملية: 300.00 SAR
            المعرف البديل الايبان: 6810
            في: 2026-08-03 14:32
        """.trimIndent()
        val parsed = BankParserRegistry.parse("البنك الأهلي السعودي", body, 1_725_000_000_000L)
        assertEquals("SNB", parsed.parserName)
        assertEquals(0, BigDecimal("300.00").compareTo(parsed.amount))
        assertTrue(parsed.identifierEvidence.any { it.lastFour == "3001" })
    }
}
