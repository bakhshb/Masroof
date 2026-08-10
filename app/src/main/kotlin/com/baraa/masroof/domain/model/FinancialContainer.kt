package com.baraa.masroof.domain.model

/**
 * Where money or debt is held.
 *
 * Concrete containers today are [Account] and [Card]. Further container kinds
 * (for example investment-only vehicles) can extend this sealed hierarchy later.
 */
sealed interface FinancialContainer {
    val id: String
    val bank: Bank
    val ownership: OwnershipStatus
    val displayName: String?
}
