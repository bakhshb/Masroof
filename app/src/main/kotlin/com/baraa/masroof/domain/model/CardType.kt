package com.baraa.masroof.domain.model

/**
 * Kind of [Card].
 *
 * Drawn from PRD card support (credit / debit). DOMAIN.md references [CardType]
 * without listing members.
 */
enum class CardType {
    DEBIT,
    CREDIT,
    OTHER,
}
