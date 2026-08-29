package com.baraa.masroof.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MasroofLineChartTest {
    @Test
    fun valueRange_includesReferenceAndZero() {
        val range = MasroofLineChart.valueRange(
            values = listOf(BigDecimal("20"), BigDecimal("-10")),
            reference = BigDecimal("35"),
        )

        assertEquals(BigDecimal("-10"), range.minimum)
        assertEquals(BigDecimal("35"), range.maximum)
    }

    @Test
    fun valueRange_expandsFlatSeriesForRendering() {
        val range = MasroofLineChart.valueRange(
            values = listOf(BigDecimal.ZERO, BigDecimal.ZERO),
            reference = BigDecimal.ZERO,
        )

        assertEquals(BigDecimal.ZERO, range.minimum)
        assertEquals(BigDecimal.ONE, range.maximum)
    }

    @Test
    fun nearestPointIndex_selectsClosestPointAndClampsToChartBounds() {
        assertEquals(0, MasroofLineChart.nearestPointIndex(x = -10f, width = 100f, pointCount = 3))
        assertEquals(1, MasroofLineChart.nearestPointIndex(x = 54f, width = 100f, pointCount = 3))
        assertEquals(2, MasroofLineChart.nearestPointIndex(x = 120f, width = 100f, pointCount = 3))
    }

    @Test
    fun nearestPointIndex_handlesSingleAndInvalidCharts() {
        assertEquals(0, MasroofLineChart.nearestPointIndex(x = 50f, width = 100f, pointCount = 1))
        assertEquals(null, MasroofLineChart.nearestPointIndex(x = 50f, width = 0f, pointCount = 3))
        assertEquals(null, MasroofLineChart.nearestPointIndex(x = 50f, width = 100f, pointCount = 0))
    }
}
