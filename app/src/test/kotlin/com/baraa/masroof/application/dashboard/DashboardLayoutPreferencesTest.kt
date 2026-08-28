package com.baraa.masroof.application.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutPreferencesTest {
    @Test
    fun withMergedSections_preservesCustomOrderWhenComplete() {
        val custom = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.TRANSACTIONS),
                entry(DashboardSectionId.HERO),
                entry(DashboardSectionId.CARDS),
                entry(DashboardSectionId.LOANS),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.QUICK),
            ),
        )

        val merged = custom.withMergedSections()

        assertEquals(custom, merged)
        assertEquals(
            listOf(
                DashboardSectionId.TRANSACTIONS,
                DashboardSectionId.HERO,
                DashboardSectionId.CARDS,
                DashboardSectionId.LOANS,
                DashboardSectionId.ACCOUNTS,
                DashboardSectionId.QUICK,
            ),
            merged.sections.map { it.id },
        )
    }

    @Test
    fun withMergedSections_insertsLoansWithoutReorderingExistingSections() {
        val legacy = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.HERO),
                entry(DashboardSectionId.TRANSACTIONS),
                entry(DashboardSectionId.CARDS),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.QUICK),
            ),
        )

        val merged = legacy.withMergedSections()

        assertEquals(
            listOf(
                DashboardSectionId.HERO,
                DashboardSectionId.LOANS,
                DashboardSectionId.TRANSACTIONS,
                DashboardSectionId.CARDS,
                DashboardSectionId.ACCOUNTS,
                DashboardSectionId.QUICK,
            ),
            merged.sections.map { it.id },
        )
    }

    @Test
    fun withMergedSections_insertsLoansBeforeTransactionsInDefaultLayout() {
        val preLoansDefaultOrder = DashboardLayoutSnapshot(
            sections = listOf(
                entry(DashboardSectionId.HERO, DashboardSectionSize.LARGE),
                entry(DashboardSectionId.QUICK),
                entry(DashboardSectionId.ACCOUNTS),
                entry(DashboardSectionId.CARDS, DashboardSectionSize.LARGE),
                entry(DashboardSectionId.TRANSACTIONS),
            ),
        )

        val merged = preLoansDefaultOrder.withMergedSections()

        assertEquals(
            listOf(
                DashboardSectionId.HERO,
                DashboardSectionId.QUICK,
                DashboardSectionId.ACCOUNTS,
                DashboardSectionId.CARDS,
                DashboardSectionId.LOANS,
                DashboardSectionId.TRANSACTIONS,
            ),
            merged.sections.map { it.id },
        )
        assertEquals(DashboardSectionSize.MEDIUM, merged.entry(DashboardSectionId.LOANS)!!.size)
        assertTrue(merged.entry(DashboardSectionId.LOANS)!!.visible)
    }

    @Test
    fun defaultLayout_includesLoansBetweenCardsAndTransactions() {
        val defaults = DashboardLayoutSnapshot.default()

        val ids = defaults.sections.map { it.id }
        assertTrue(DashboardSectionId.LOANS in ids)
        assertTrue(ids.indexOf(DashboardSectionId.LOANS) > ids.indexOf(DashboardSectionId.CARDS))
        assertTrue(ids.indexOf(DashboardSectionId.LOANS) < ids.indexOf(DashboardSectionId.TRANSACTIONS))
    }

    private fun entry(
        id: DashboardSectionId,
        size: DashboardSectionSize = DashboardSectionSize.MEDIUM,
    ): DashboardSectionEntry =
        DashboardSectionEntry(id = id, visible = true, size = size)
}
