package com.baraa.masroof.domain.model

/**
 * Bank-scoped loan container identity for registry lookup and assembly.
 */
data class LoanReference(
    val bank: Bank,
    val loanType: LoanType,
)
