package com.baraa.masroof.ui.charts

import androidx.compose.ui.graphics.Color
import java.math.BigDecimal

/** One slice of a donut / composition chart. */
data class ChartSlice(
    val id: String,
    val label: String,
    val value: BigDecimal,
    val color: Color,
)

/** One point in a daily column / trend series. */
data class DailyChartPoint(
    val dayOfMonth: Int,
    val value: BigDecimal,
    val label: String = dayOfMonth.toString(),
)
