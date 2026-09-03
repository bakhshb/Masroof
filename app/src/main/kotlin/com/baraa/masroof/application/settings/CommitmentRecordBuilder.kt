package com.baraa.masroof.application.settings

import com.baraa.masroof.domain.model.Commitment
import java.time.Instant

enum class CommitmentRecordEvent {
    CREATED,
    PAUSED,
    RESUMED,
}

data class CommitmentRecordEntry(
    val commitmentId: String,
    val commitmentName: String,
    val event: CommitmentRecordEvent,
    val at: Instant,
)

object CommitmentRecordBuilder {
    fun recordTabItems(commitments: List<Commitment>): List<CommitmentRecordEntry> =
        commitments
            .flatMap { commitment -> eventsFor(commitment) }
            .sortedWith(
                compareByDescending<CommitmentRecordEntry> { it.at }
                    .thenBy { it.commitmentName.lowercase() }
                    .thenBy { it.event.ordinal },
            )

    private fun eventsFor(commitment: Commitment): List<CommitmentRecordEntry> = buildList {
        add(
            CommitmentRecordEntry(
                commitmentId = commitment.id,
                commitmentName = commitment.name,
                event = CommitmentRecordEvent.CREATED,
                at = commitment.createdAt,
            ),
        )
        commitment.pauseIntervals.forEach { interval ->
            add(
                CommitmentRecordEntry(
                    commitmentId = commitment.id,
                    commitmentName = commitment.name,
                    event = CommitmentRecordEvent.PAUSED,
                    at = interval.pausedAt,
                ),
            )
            interval.resumedAt?.let { resumedAt ->
                add(
                    CommitmentRecordEntry(
                        commitmentId = commitment.id,
                        commitmentName = commitment.name,
                        event = CommitmentRecordEvent.RESUMED,
                        at = resumedAt,
                    ),
                )
            }
        }
    }
}
