package com.baraa.masroof.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable monetary amount with an explicit currency.
 *
 * Amounts are stored as [BigDecimal] with a fixed scale of [SCALE] using
 * [RoundingMode.HALF_EVEN]. Arithmetic requires matching currencies.
 *
 * Sign convention: domain models store non-negative magnitudes; direction and
 * transaction type express economic meaning. Construction rejects negative values.
 */
data class Money(
    val amount: BigDecimal,
    val currency: Currency,
) {
    init {
        require(amount >= BigDecimal.ZERO) {
            "Money amount must be non-negative, was $amount"
        }
        require(amount.scale() <= SCALE) {
            "Money amount scale must be <= $SCALE, was ${amount.scale()}"
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount).setScale(SCALE), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        val result = amount.subtract(other.amount)
        require(result >= BigDecimal.ZERO) {
            "Money subtraction would be negative: $amount - ${other.amount}"
        }
        return Money(result.setScale(SCALE), currency)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int {
        var result = amount.stripTrailingZeros().hashCode()
        result = 31 * result + currency.hashCode()
        return result
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: $currency vs ${other.currency}"
        }
    }

    companion object {
        const val SCALE: Int = 2

        fun of(amount: String, currency: Currency): Money =
            Money(normalize(BigDecimal(amount)), currency)

        fun of(amount: BigDecimal, currency: Currency): Money =
            Money(normalize(amount), currency)

        fun zero(currency: Currency): Money =
            Money(BigDecimal.ZERO.setScale(SCALE), currency)

        private fun normalize(value: BigDecimal): BigDecimal =
            value.setScale(SCALE, RoundingMode.HALF_EVEN)
    }
}
