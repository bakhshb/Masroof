package com.baraa.masroof.domain.model

/**
 * Direction of money movement relative to the referenced account or card.
 *
 * Does not define income or expense.
 */
enum class MoneyDirection {
    INCOMING,
    OUTGOING,
    NEUTRAL,
    UNKNOWN,
}
