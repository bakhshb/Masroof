package com.baraa.masroof.transaction

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class LineBasedAmountExtractionTest {
    private val parser = GenericBankSmsParser()
    @Test fun purchaseAmountSelectedInsteadOfBalance() {
        val body = "Purchase\nPurchase Amount: 51.99 SAR\nAvailable Balance: 17230.03 SAR\nTotal Amount Due: 2380.88 SAR"
        val r = parser.parse("bank", body, 0)
        assertEquals(BigDecimal("51.99"), r.amount)
    }
    @Test fun explicitBimablighIsAccepted() {
        val r = parser.parse("bank", "بمبلغ: 51.99 SAR", 0)
        assertEquals(BigDecimal("51.99"), r.amount)
    }
    @Test fun amountLineWithoutLabelIsRejected() {
        val r = parser.parse("bank", "51.99 SAR", 0)
        assertNull(r.amount)
    }
    @Test fun totalAmountDueIsRejected() {
        val r = parser.parse("bank", "Purchase Amount: 51.99 SAR\nTotal Amount Due: 2380.88 SAR", 0)
        assertEquals(BigDecimal("51.99"), r.amount)
    }
    @Test fun cardLineWithDigitsIsLastFour() {
        val r = parser.parse("bank", "Card: 1234\nPurchase Amount: 300 SAR", 0)
        assertEquals("1234", r.accountOrCardLastFourDigits)
    }
    @Test fun balanceOnlyMessageIsRejected() {
        val r = parser.parse("bank", "Available Balance: 4500 SAR", 0)
        assertNull(r.amount)
    }
}