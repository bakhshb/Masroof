package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.DashboardSectionSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardSectionMetricsTest {
    @Test
    fun sizeTokens_smallIsVisiblySmallerThanLarge() {
        val small = dashboardSectionMetrics(DashboardSectionSize.SMALL)
        val medium = dashboardSectionMetrics(DashboardSectionSize.MEDIUM)
        val large = dashboardSectionMetrics(DashboardSectionSize.LARGE)

        assertTrue(small.heroAmountScale < medium.heroAmountScale)
        assertTrue(medium.heroAmountScale < large.heroAmountScale)

        assertTrue(small.quickMinHeight < medium.quickMinHeight)
        assertTrue(medium.quickMinHeight < large.quickMinHeight)

        assertTrue(small.quickIconSize < medium.quickIconSize)
        assertTrue(medium.quickIconSize < large.quickIconSize)

        assertTrue(small.accountIconSize < medium.accountIconSize)
        assertTrue(medium.accountIconSize < large.accountIconSize)

        assertTrue(small.cardWidth < medium.cardWidth)
        assertTrue(medium.cardWidth < large.cardWidth)

        assertTrue(small.cardMinHeight < medium.cardMinHeight)
        assertTrue(medium.cardMinHeight < large.cardMinHeight)

        assertTrue(small.heroProgressHeight < large.heroProgressHeight)
        assertFalse(small.showHeroFooter)
        assertTrue(large.showHeroFooter)
        assertFalse(small.showAccountPeriodFlow)
        assertTrue(large.showAccountPeriodFlow)
    }

    @Test
    fun recentTransactionCount_scalesWithSectionSize() {
        assertEquals(3, dashboardSectionMetrics(DashboardSectionSize.SMALL).recentTransactionCount)
        assertEquals(5, dashboardSectionMetrics(DashboardSectionSize.MEDIUM).recentTransactionCount)
        assertEquals(8, dashboardSectionMetrics(DashboardSectionSize.LARGE).recentTransactionCount)
        assertEquals(
            dashboardSectionMetrics(DashboardSectionSize.LARGE).recentTransactionCount,
            DASHBOARD_RECENT_TRANSACTION_LIMIT,
        )
    }

    @Test
    fun heroAndQuickHelpers_matchMetrics() {
        val large = DashboardSectionSize.LARGE
        assertEquals(dashboardSectionMetrics(large).heroAmountScale, heroAmountStyleScale(large), 0.001f)
        assertEquals(dashboardSectionMetrics(large).quickPadding, quickCardPadding(large))
    }
}
