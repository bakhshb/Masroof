package com.baraa.masroof.bank.aljazira.extraction

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class InternationalPurchaseFactsExtractorTest {
    @Test
    fun extractsExchangeRateAndFee() {
        val body = """
            بمبلغ: USD 23.00
            رسوم العمليات الدولية: 1.99
            سعر الصرف: 3.756957
        """.trimIndent()
        val facts = InternationalPurchaseFactsExtractor.extract(body)
        assertNotNull(facts)
        assertEquals(BigDecimal("3.756957"), facts!!.exchangeRate)
        assertEquals(Money.of("1.99", Currency.SAR), facts.internationalFee)
    }

    @Test
    fun missingExchangeRate_returnsNull() {
        assertNull(InternationalPurchaseFactsExtractor.extract("بمبلغ: USD 23.00"))
    }
}
