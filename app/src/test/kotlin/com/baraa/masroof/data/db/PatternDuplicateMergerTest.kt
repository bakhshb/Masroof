package com.baraa.masroof.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternDuplicateMergerTest {

    private fun row(
        id: Long,
        status: String,
        userConfirmed: Boolean = false,
        exampleCount: Int = 1,
        createdAt: Long = 100L,
    ) = PatternDuplicateMerger.MergeRow(
        id = id,
        senderProfileId = 1L,
        canonicalKey = "T:x",
        status = status,
        userConfirmed = userConfirmed,
        exampleCount = exampleCount,
        createdAt = createdAt,
    )

    @Test
    fun userConfirmedRowWinsOverBetterStatus() {
        val group = listOf(
            row(1, "APPROVED", userConfirmed = false),
            row(2, "IGNORED", userConfirmed = true),
        )
        assertEquals(2L, PatternDuplicateMerger.selectSurvivor(group).id)
    }

    @Test
    fun statusPriorityBreaksTiesAmongUnconfirmed() {
        val group = listOf(
            row(1, "UNKNOWN"),
            row(2, "DEPRECATED"),
            row(3, "APPROVED"),
            row(4, "IGNORED"),
        )
        assertEquals(3L, PatternDuplicateMerger.selectSurvivor(group).id)
    }

    @Test
    fun earliestCreatedAtBreaksStatusTies() {
        val group = listOf(
            row(1, "APPROVED", createdAt = 300L),
            row(2, "APPROVED", createdAt = 100L),
            row(3, "APPROVED", createdAt = 200L),
        )
        assertEquals(2L, PatternDuplicateMerger.selectSurvivor(group).id)
    }

    @Test
    fun confirmedIgnoredSurvivesUnknownDuplicate() {
        // The exact scenario from the migration plan: one userConfirmed
        // IGNORED row plus a later UNKNOWN duplicate.
        val group = listOf(
            row(1, "IGNORED", userConfirmed = true, exampleCount = 4, createdAt = 100L),
            row(2, "UNKNOWN", userConfirmed = false, exampleCount = 3, createdAt = 200L),
        )
        assertEquals(1L, PatternDuplicateMerger.selectSurvivor(group).id)
        assertEquals(7, PatternDuplicateMerger.mergedExampleCount(group))
    }

    @Test
    fun singleRowGroupSurvivesUnchanged() {
        val only = row(9, "UNKNOWN", exampleCount = 5)
        assertEquals(only, PatternDuplicateMerger.selectSurvivor(listOf(only)))
        assertEquals(5, PatternDuplicateMerger.mergedExampleCount(listOf(only)))
    }
}
