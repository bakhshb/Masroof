package com.baraa.masroof.core.money

/**
 * Supported currency codes for Masroof.
 */
enum class Currency {
    SAR,
    USD,
    EUR,
    GBP,
    ;

    fun convertsToSar(): Boolean = this in FOREIGN_FOR_SAR_CONVERSION

    companion object {
        val FOREIGN_FOR_SAR_CONVERSION: Set<Currency> = setOf(USD, EUR, GBP)
    }
}
