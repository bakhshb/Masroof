package com.baraa.masroof.application.settings

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentPauseInterval
import com.baraa.masroof.domain.model.CommitmentRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CommitmentRecordBuilderTest {
    @Test
    fun recordTabItems_includesCreatedEventForEveryCommitment() {
        val commitment = sampleCommitment(id = "one")

        val entries = CommitmentRecordBuilder.recordTabItems(listOf(commitment))

        assertEquals(1, entries.size)
        assertEquals(CommitmentRecordEvent.CREATED, entries.single().event)
        assertEquals(commitment.createdAt, entries.single().at)
    }

    @Test
    fun recordTabItems_includesPauseAndResumeEvents() {
        val pausedAt = Instant.parse("2026-04-01T00:00:00Z")
        val resumedAt = Instant.parse("2026-09-01T00:00:00Z")
        val commitment = sampleCommitment(active = false).copy(
            pauseIntervals = listOf(
                CommitmentPauseInterval(pausedAt = pausedAt, resumedAt = resumedAt),
            ),
        )

        val events = CommitmentRecordBuilder.recordTabItems(listOf(commitment)).map { it.event }

        assertEquals(3, events.size)
        assertEquals(1, events.count { it == CommitmentRecordEvent.CREATED })
        assertEquals(1, events.count { it == CommitmentRecordEvent.PAUSED })
        assertEquals(1, events.count { it == CommitmentRecordEvent.RESUMED })
        assertEquals(CommitmentRecordEvent.RESUMED, CommitmentRecordBuilder.recordTabItems(listOf(commitment)).first().event)
    }

    @Test
    fun recordTabItems_sortedNewestFirstAcrossCommitments() {
        val older = sampleCommitment(id = "older", createdAt = Instant.parse("2026-01-01T00:00:00Z"))
        val newer = sampleCommitment(id = "newer", createdAt = Instant.parse("2026-09-01T00:00:00Z"))

        val entries = CommitmentRecordBuilder.recordTabItems(listOf(older, newer))

        assertEquals("newer", entries.first().commitmentId)
        assertEquals("older", entries.last().commitmentId)
    }

    @Test
    fun recordTabItems_openPause_emitsPausedOnly() {
        val commitment = sampleCommitment(active = false).copy(
            pauseIntervals = listOf(
                CommitmentPauseInterval(
                    pausedAt = Instant.parse("2026-04-01T00:00:00Z"),
                    resumedAt = null,
                ),
            ),
        )

        val events = CommitmentRecordBuilder.recordTabItems(listOf(commitment)).map { it.event }

        assertTrue(events.contains(CommitmentRecordEvent.PAUSED))
        assertEquals(0, events.count { it == CommitmentRecordEvent.RESUMED })
    }

    private fun sampleCommitment(
        id: String = RegistryEntityIdFactory.newCommitmentId(),
        active: Boolean = true,
        createdAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
    ): Commitment =
        Commitment(
            id = id,
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            transactionDate = LocalDate.parse("2026-08-01"),
            recurrence = CommitmentRecurrence.MONTHLY,
            dueDate = null,
            active = active,
            sourceTransactionId = "tx-$id",
            createdAt = createdAt,
            updatedAt = createdAt,
        )
}
