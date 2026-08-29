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
}
