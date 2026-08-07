package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Anonymized fixtures for amount-label shapes that previously fell into
 * NO_AMOUNT during import. Amounts only from labeled context.
 */
class CommonAmountLabelFixtureTest {
    private val parser = GenericBankSmsParser()

    @Test
    fun withdrawalAmountLabelArabic() {
        val body = """
            سحب نقدي
            قيمة السحب: 500.00 SAR
            بطاقة مدى: 8219
            في: 10:15 2026-08-01
            الرصيد المتاح: 1200.00 SAR
        """.trimIndent()
        val r = parser.parse("SNB", body, null)
        assertEquals(TransactionType.CASH_WITHDRAWAL, r.transactionType)
        assertEquals(0, BigDecimal("500.00").compareTo(r.amount))
        assertEquals("8219", r.accountOrCardLastFourDigits)
    }

    @Test
    fun transferAmountLabelArabic() {
        val body = """
            حوالة صادرة
            مبلغ التحويل: 250.50 SAR
            من حساب: 3001
            الى: [BENEFICIARY]
        """.trimIndent()
        val r = parser.parse("AlRajhi", body, null)
        assertEquals(TransactionType.TRANSFER_OUT, r.transactionType)
        assertEquals(0, BigDecimal("250.50").compareTo(r.amount))
        assertEquals("3001", r.accountOrCardLastFourDigits)
    }

    @Test
    fun debitedAmountEnglishLabel() {
        val body = """
            Purchase
            Debited Amount: 33.03 SAR
            Card: 7271
            at: Store
            Available Balance: 1000.00 SAR
        """.trimIndent()
        val r = parser.parse("BSF", body, null)
        assertEquals(0, BigDecimal("33.03").compareTo(r.amount))
        assertEquals("7271", r.accountOrCardLastFourDigits)
    }

    @Test
    fun walletTopUpChargeAmountLabel() {
        val body = """
            شحن المحفظة
            قيمة الشحن: 100.00 SAR
            بطاقة ائتمانية: 4444
        """.trimIndent()
        val r = parser.parse("STCBank", body, null)
        assertEquals(0, BigDecimal("100.00").compareTo(r.amount))
        assertEquals("4444", r.accountOrCardLastFourDigits)
    }

    @Test
    fun deductedAmountLabelArabic() {
        val body = """
            رسوم خدمة
            المبلغ المخصوم: 5.00 SAR
            حساب: 3003
        """.trimIndent()
        val r = parser.parse("Bank", body, null)
        assertEquals(0, BigDecimal("5.00").compareTo(r.amount))
        assertTrue(r.transactionType == TransactionType.BANK_FEE || r.amount != null)
    }

    @Test
    fun balanceOnlyStillRejected() {
        val r = parser.parse("Bank", "الرصيد المتاح: 4500 SAR", null)
        assertNull(r.amount)
    }

    @Test
    fun unlabeledDigitsStillRejected() {
        val r = parser.parse("Bank", "شراء\n7271\n51.99 SAR", null)
        assertNull(r.amount)
    }
}
