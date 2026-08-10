package com.baraa.masroof.domain.model

/**
 * Kind of [Account] currently in product scope (PRD §7.3).
 *
 * Investment and other future containers are deferred until the product
 * requires them — they are not invented here.
 */
enum class AccountType {
    CURRENT,
    SAVINGS,
    WALLET,
}
