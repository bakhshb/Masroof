package com.baraa.masroof.domain.rules

/**
 * Derived economic impact of a classified financial outcome.
 *
 * Distinct from [com.baraa.masroof.domain.model.FinancialTransactionType]: a type
 * such as EXTERNAL_TRANSFER_IN is cash movement, not automatically income.
 */
data class FinancialImpact(
    val countsAsExpense: Boolean,
    val countsAsIncome: Boolean,
    val netWorthEffect: NetWorthEffect,
)

/**
 * Direction of net-worth change implied by a classification.
 *
 * [UNRESOLVED] means the domain cannot safely assert an effect yet.
 */
enum class NetWorthEffect {
    ZERO,
    INCREASE,
    DECREASE,
    UNRESOLVED,
}
