package com.baraa.masroof.domain.model

import com.baraa.masroof.core.money.Money
import java.time.Instant

/**
 * User-supplied correction for a parsed event.
 *
 * Stored separately from [RawSms]; never mutates original SMS evidence.
 */
data class UserCorrection(
    val id: String,
    val targetEventId: String,
    val correctedType: MessageFamily?,
    val correctedAmount: Money?,
    val correctedMerchant: String?,
    val correctedOwnership: OwnershipStatus?,
    val correctedCounterparty: String?,
    val createdAt: Instant,
)
