package com.baraa.masroof.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AmountExtractionSafetyTest {
    private val parser = GenericBankSmsParser()
    @Test fun purchaseAmountWinsOverCardAndBalance() {
        val p = parser.parse("bank", "Amount: 250 SAR\nCard: 1234\nBalance: 4500 SAR", 0)
        assertEquals(BigDecimal("250"), p.amount); assertEquals("1234", p.accountOrCardLastFourDigits)
    }
    @Test fun identifiersAndIsolatedNumbersAreNeverAmounts() {
        listOf("بطاقة ****5678", "Account ending in 4321", "IBAN ****1234", "OTP 7788", "reference 123456", "0501234567", "2026/08/04 23:59", "POS ID 4321", "رقم 1234").forEach {
            assertNull(it, parser.parse("bank", it, 0).amount)
        }
    }
    @Test fun explicitArabicAndEnglishCurrencyPatternsWork() {
        assertEquals(BigDecimal("250.75"), parser.parse("bank", "Purchase Amount: 250.75 SAR", 0).amount)
        assertEquals(BigDecimal("1234.50"), parser.parse("bank", "Purchase Amount: 1234.50 SAR", 0).amount)
        assertEquals(BigDecimal("89.00"), parser.parse("bank", "Purchase Amount: 89.00 USD", 0).amount)
    }
    @Test fun balanceOnlyMessageIsReviewRequired() {
        val p = parser.parse("bank", "Available Balance: 4500 SAR", 0)
        assertNull(p.amount); assertEquals(TransactionStatus.NEEDS_REVIEW, p.status); assertTrue(p.parsingNotes.any { it.contains("AMOUNT_NOT_RELIABLY_IDENTIFIED") })
    }
}