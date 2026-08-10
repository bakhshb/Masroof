package com.baraa.masroof.parsing.model

import com.baraa.masroof.core.money.Money
import java.time.LocalDateTime

/**
 * Parse-time structured facts that are not ownership or financial-treatment
 * conclusions, and that [com.baraa.masroof.domain.model.ParsedEvent] does not
 * currently carry.
 *
 * Kept as a narrowly typed parsing-layer companion to [ParseResult] /
 * [ParsedEventDraft] so fixture fields (reference, balances, biller, local
 * timestamp) are not silently discarded or conflated with merchant/amount.
 *
 * [occurredAtLocal] holds the SMS local date-time without inventing a timezone.
 * [com.baraa.masroof.domain.model.ParsedEvent.occurredAt] ([java.time.Instant])
 * remains unset until a timezone policy is defined.
 */
data class ParsedEventDetails(
    val transactionReference: String? = null,
    val availableBalance: Money? = null,
    val outstandingBalance: Money? = null,
    val biller: String? = null,
    val billerCode: String? = null,
    val occurredAtLocal: LocalDateTime? = null,
)
