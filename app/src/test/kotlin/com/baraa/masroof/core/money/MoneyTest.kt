package com.baraa.masroof.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun equality_ignoresTrailingZeros() {
        val a = Money.of("10.50", Currency.SAR)
        val b = Money(BigDecimal("10.5").setScale(2), Currency.SAR)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun addition_sameCurrency() {
        val sum = Money.of("10.00", Currency.SAR) + Money.of("2.55", Currency.SAR)
        assertEquals(Money.of("12.55", Currency.SAR), sum)
    }

    @Test
    fun subtraction_sameCurrency() {
        val result = Money.of("10.00", Currency.SAR) - Money.of("2.50", Currency.SAR)
        assertEquals(Money.of("7.50", Currency.SAR), result)
    }

    @Test
    fun subtraction_rejectsNegativeResult() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1.00", Currency.SAR) - Money.of("2.00", Currency.SAR)
        }
    }

    @Test
    fun construction_rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(BigDecimal("-0.01"), Currency.SAR)
        }
    }

    @Test
    fun construction_rejectsExcessScale() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(BigDecimal("1.234"), Currency.SAR)
        }
    }

    @Test
    fun of_normalizesScale() {
        val money = Money.of(BigDecimal("1.2"), Currency.SAR)
        assertEquals(2, money.amount.scale())
        assertEquals(Money.of("1.20", Currency.SAR), money)
    }

    @Test
    fun zero_isDistinctFromPositiveByEquality() {
        assertNotEquals(Money.zero(Currency.SAR), Money.of("0.01", Currency.SAR))
        assertEquals(Money.zero(Currency.SAR), Money.of("0.00", Currency.SAR))
    }
}
