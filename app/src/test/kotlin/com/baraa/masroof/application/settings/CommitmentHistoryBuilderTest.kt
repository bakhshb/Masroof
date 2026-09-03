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
import java.time.ZoneId

class CommitmentHistoryBuilderTest {
    private val zone = ZoneId.of("Asia/Riyadh")

    @Test
    fun historyTabItems_includesOnlyInactiveCommitments() {
        val active = sampleCommitment(id = "active", active = true)
        val paused = sampleCommitment(id = "paused", active = false)

        val items = CommitmentHistoryBuilder.disabledTabItems(listOf(active, paused), zone)

        assertEquals(1, items.size)
        assertEquals("paused", items.single().commitmentId)
    }

    @Test
    fun intervalSummaries_mapsPauseIntervalsToLocalDates() {
        val commitment = sampleCommitment(active = false).copy(
            pauseIntervals = listOf(
                CommitmentPauseInterval(
                    pausedAt = Instant.parse("2026-04-01T00:00:00Z"),
                    resumedAt = Instant.parse("2026-08-01T00:00:00Z"),
                ),
            ),
        )

        val summaries = CommitmentHistoryBuilder.intervalSummaries(commitment, zone)

        assertEquals(1, summaries.size)
        assertEquals(LocalDate.parse("2026-04-01"), summaries.single().pausedAt)
        assertEquals(LocalDate.parse("2026-08-01"), summaries.single().resumedAt)
    }

    @Test
    fun historyTabItems_sortedByLatestPauseDescending() {
        val older = sampleCommitment(id = "older", active = false).copy(
            pauseIntervals = listOf(
                CommitmentPauseInterval(
                    pausedAt = Instant.parse("2026-03-01T00:00:00Z"),
                    resumedAt = null,
                ),
            ),
        )
        val newer = sampleCommitment(id = "newer", active = false).copy(
            pauseIntervals = listOf(
                CommitmentPauseInterval(
                    pausedAt = Instant.parse("2026-09-01T00:00:00Z"),
                    resumedAt = null,
                ),
            ),
        )

        val items = CommitmentHistoryBuilder.disabledTabItems(listOf(older, newer), zone)

        assertEquals("newer", items.first().commitmentId)
        assertEquals("older", items.last().commitmentId)
    }

    @Test
    fun intervalSummaries_returnsEmptyForCommitmentsWithoutIntervals() {
        val commitment = sampleCommitment(active = false)

        val summaries = CommitmentHistoryBuilder.intervalSummaries(commitment, zone)

        assertTrue(summaries.isEmpty())
    }

    private fun sampleCommitment(
        id: String = RegistryEntityIdFactory.newCommitmentId(),
        active: Boolean = true,
        updatedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
    ): Commitment {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        return Commitment(
            id = id,
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            transactionDate = LocalDate.parse("2026-08-01"),
            recurrence = CommitmentRecurrence.MONTHLY,
            dueDate = null,
            active = active,
            sourceTransactionId = "tx-$id",
            createdAt = now,
            updatedAt = updatedAt,
        )
    }
}
