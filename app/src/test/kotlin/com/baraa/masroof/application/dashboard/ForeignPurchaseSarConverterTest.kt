package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ForeignPurchaseSarConverterTest {
    @Test
    fun usdPurchase_convertsWithFee() {
        val body = """
            بمبلغ: USD 23.00
            رسوم العمليات الدولية: 1.99
            سعر الصرف: 3.756957
        """.trimIndent()
        val sar = ForeignPurchaseSarConverter.toSarEquivalent(Money.of("23.00", Currency.USD), body)
        assertNotNull(sar)
        assertEquals(Currency.SAR, sar!!.currency)
        // 23 * 3.756957 = 86.409011 + 1.99 fee
        assertEquals(Money.of("88.40", Currency.SAR), sar)
    }

    @Test
    fun usdRefund_withoutFeeWhenRateInSameSms() {
        val body = """
            مبلغ: 6.51 USD
            سعر الصرف: 3.756957
        """.trimIndent()
        val sar = ForeignPurchaseSarConverter.toSarEquivalent(
            foreignAmount = Money.of("6.51", Currency.USD),
            rawSmsBody = body,
            includeInternationalFee = false,
        )
        assertNotNull(sar)
        assertEquals(Money.of("24.46", Currency.SAR), sar)
    }
}
