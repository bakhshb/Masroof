package com.baraa.masroof.ui.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.math.BigDecimal

/**
 * Vico 1.13 column chart for daily numeric series (expenses or liquidity).
 * Caller should gate empty/all-zero series with [ChartCard.isEmpty].
 */
@Composable
fun DailyTrendColumnChart(
    points: List<DailyChartPoint>,
    modifier: Modifier = Modifier,
    columnColorArgb: Int? = null,
) {
    if (points.isEmpty()) return
    val modelProducer = remember { ChartEntryModelProducer() }
    val entries = remember(points) {
        points.map { point ->
            entryOf(x = point.dayOfMonth.toFloat(), y = point.value.toFloatSafe())
        }
    }
    val color = columnColorArgb ?: MaterialTheme.colorScheme.secondary.toArgb()

    LaunchedEffect(entries) {
        modelProducer.setEntries(entries)
    }

    ProvideChartStyle(m3ChartStyle(entityColors = listOf(androidx.compose.ui.graphics.Color(color)))) {
        val columnChart = columnChart(
            columns = currentChartStyle.columnChart.columns,
            axisValuesOverrider = AxisValuesOverrider.adaptiveYValues(yFraction = 1.2f, round = true),
        )
        Chart(
            chart = columnChart,
            chartModelProducer = modelProducer,
            startAxis = rememberStartAxis(
                itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 4),
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    val day = value.toInt()
                    if (day == 1 || day % 5 == 0 || day == points.size) day.toString() else ""
                },
                itemPlacer = AxisItemPlacer.Horizontal.default(spacing = 1, addExtremeLabelPadding = true),
            ),
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            isZoomEnabled = false,
        )
    }
}

private fun BigDecimal.toFloatSafe(): Float =
    runCatching { toFloat() }.getOrDefault(0f)
