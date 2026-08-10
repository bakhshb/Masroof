package com.baraa.masroof.domain.model

/**
 * Kind of [Account] container.
 *
 * Drawn from PRD account support (current, savings, wallets, investment later).
 * DOMAIN.md references [AccountType] without listing members.
 */
enum class AccountType {
    CURRENT,
    SAVINGS,
    WALLET,
    INVESTMENT,
    OTHER,
}
