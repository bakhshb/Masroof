package com.baraa.masroof.transaction

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Regression tests for the four supplied sanitized bank SMS formats.
 * They verify that the parser never selects card/account/IBAN/Mada
 * last-fours as the transaction amount.
 */
class SuppliedBankSmsTest {
    private val parser = GenericBankSmsParser()

    @Test fun creditCardOnlinePurchase() {
        val body = """
            شراء عبر الانترنت
            بطاقة ائتمانية: 7271
            بمبلغ :51.99 SAR
            لدى :Keeta
            في :22:50 03-08-2026
            الرصيد المتاح :SAR 17230.03
            إجمالي المبلغ المستحق:2380.88 SAR
        """.trimIndent()
        val r = parser.parse(null, body, null)
        assertEquals(TransactionType.ONLINE_PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("51.99").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals("7271", r.accountOrCardLastFourDigits)
        assertEquals("Keeta", r.merchant)
    }

    @Test fun outgoingTransfer() {
        val body = """
            عملية حوالة مالية صادرة مقبولة
            خصمت من حساب: 3001
            الى: [BENEFICIARY]
            مبلغ العملية: 300.00 SAR
            المعرف البديل \الايبان : 6810
            [DESTINATION_BANK]
            في: 2026-08-03 14:32
            رقم المعاملة: 2BTMS11432672163
        """.trimIndent()
        val r = parser.parse(null, body, null)
        assertEquals(TransactionType.TRANSFER_OUT, r.transactionType)
        assertEquals(0, BigDecimal("300.00").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals("3001", r.accountOrCardLastFourDigits)
        assertNull("IBAN must not become cardLastFour", null)
    }

    @Test fun madaGooglePayPurchase() {
        val body = """
            شراء عبر نقاط البيع (Google Pay)
            لدى: MALAYSIA FOODS RESTA
            بمبلغ: 127.00 SAR
            في: 13:24 2026-08-03
            بطاقة مدى رقم: 8219
        """.trimIndent()
        val r = parser.parse(null, body, null)
        assertEquals(TransactionType.PURCHASE, r.transactionType)
        assertEquals(0, BigDecimal("127.00").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
        assertEquals("MALAYSIA FOODS RESTA", r.merchant)
        assertEquals("8219", r.accountOrCardLastFourDigits)
    }

    @Test fun incomingTransfer() {
        val body = """
            حوالة واردة داخلية
            مبلغ: SAR 4,445.67
            إلى: 3003
            اسم المرسل: [SENDER_NAME]
            رقم حساب المرسل: 3001
            البنك المرسل: بنك الجزيرة
            في: 2026-08-03 10:38
        """.trimIndent()
        val r = parser.parse(null, body, null)
        assertEquals(TransactionType.INTERNAL_TRANSFER, r.transactionType)
        assertEquals(0, BigDecimal("4445.67").compareTo(r.amount))
        assertEquals(Currency.SAR, r.currency)
    }

    @Test fun balanceOnlyMessageYieldsNoAmount() {
        val r = parser.parse(null, "الرصيد المتاح: 4500 SAR", null)
        assertNull(r.amount)
        assertEquals(TransactionStatus.NEEDS_REVIEW, r.status)
    }
}
