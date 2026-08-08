package com.baraa.masroof.data.db

/**
 * Pure survivor-selection rule for merging duplicate pattern rows that share
 * one canonical identity. Used by MIGRATION_24_25 and unit-tested directly.
 */
object PatternDuplicateMerger {

    data class MergeRow(
        val id: Long,
        val senderProfileId: Long,
        val canonicalKey: String,
        val status: String,
        val userConfirmed: Boolean,
        val exampleCount: Int,
        val createdAt: Long,
    )

    /** APPROVED > IGNORED > DEPRECATED > UNKNOWN; user-confirmed rows always win. */
    fun statusPriority(status: String): Int = when (status) {
        "APPROVED" -> 0
        "IGNORED" -> 1
        "DEPRECATED" -> 2
        else -> 3
    }

    fun selectSurvivor(group: List<MergeRow>): MergeRow =
        group.sortedWith(
            compareByDescending<MergeRow> { it.userConfirmed }
                .thenBy { statusPriority(it.status) }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        ).first()

    fun mergedExampleCount(group: List<MergeRow>): Int = group.sumOf { it.exampleCount }
}
