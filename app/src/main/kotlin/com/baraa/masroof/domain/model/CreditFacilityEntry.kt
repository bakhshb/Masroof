package com.baraa.masroof.domain.model

/**
 * Credit account / facility grouping primary and supplementary cards.
 *
 * Statement due is computed in dashboard builders — not stored here.
 */
data class CreditFacilityEntry(
    val id: String,
    val bank: Bank,
    val primaryLast4: String,
    val displayName: String? = null,
)
