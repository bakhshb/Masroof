package com.baraa.masroof.application.settings

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import java.time.LocalDate
import java.time.ZoneId

data class CommitmentHistoryItem(
    val commitmentId: String,
    val name: String,
    val amount: Money,
    val recurrence: CommitmentRecurrence?,
    val transactionDate: LocalDate,
    val sourceTransactionId: String,
    val intervals: List<CommitmentPauseIntervalSummary>,
)

data class CommitmentPauseIntervalSummary(
    val pausedAt: LocalDate,
    val resumedAt: LocalDate?,
)

object CommitmentHistoryBuilder {
    fun historyTabItems(
        commitments: List<Commitment>,
        zoneId: ZoneId,
    ): List<CommitmentHistoryItem> =
        commitments
            .filter { commitment -> !commitment.active }
            .map { commitment -> toHistoryItem(commitment, zoneId) }
            .sortedByDescending { item ->
                item.intervals.lastOrNull()?.pausedAt ?: item.transactionDate
            }

    fun intervalSummaries(
        commitment: Commitment,
        zoneId: ZoneId,
    ): List<CommitmentPauseIntervalSummary> =
        commitment.pauseIntervals.map { interval ->
            CommitmentPauseIntervalSummary(
                pausedAt = interval.pausedAt.atZone(zoneId).toLocalDate(),
                resumedAt = interval.resumedAt?.atZone(zoneId)?.toLocalDate(),
            )
        }

    private fun toHistoryItem(
        commitment: Commitment,
        zoneId: ZoneId,
    ): CommitmentHistoryItem =
        CommitmentHistoryItem(
            commitmentId = commitment.id,
            name = commitment.name,
            amount = commitment.amount,
            recurrence = commitment.recurrence,
            transactionDate = commitment.transactionDate,
            sourceTransactionId = commitment.sourceTransactionId,
            intervals = intervalSummaries(commitment, zoneId),
        )
}
