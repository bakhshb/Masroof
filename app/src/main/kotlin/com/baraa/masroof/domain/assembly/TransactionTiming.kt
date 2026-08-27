package com.baraa.masroof.domain.assembly

import com.baraa.masroof.domain.matching.TransferMatchCandidate
import com.baraa.masroof.domain.model.ParsedEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Resolves the instant used for [com.baraa.masroof.domain.model.FinancialTransaction.occurredAt].
 *
 * SMS body local time ([ParsedEventDetails.occurredAtLocal]) is preferred over inbox
 * [receivedAt] so salary-period filtering matches the bank-stated transaction time.
 */
object TransactionTiming {
    fun effectiveOccurredAt(
        event: ParsedEvent,
        occurredAtLocal: LocalDateTime?,
        receivedAt: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Instant =
        event.occurredAt
            ?: occurredAtLocal?.atZone(zoneId)?.toInstant()
            ?: receivedAt

    fun effectiveOccurredAt(candidate: TransferMatchCandidate, zoneId: ZoneId): Instant =
        effectiveOccurredAt(
            event = candidate.event,
            occurredAtLocal = candidate.occurredAtLocal,
            receivedAt = candidate.receivedAt,
            zoneId = zoneId,
        )

    fun earliestEffectiveOccurredAt(
        candidates: List<TransferMatchCandidate>,
        zoneId: ZoneId,
    ): Instant? =
        candidates.map { effectiveOccurredAt(it, zoneId) }.minOrNull()
}
