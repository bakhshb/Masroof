package com.baraa.masroof.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.FinancialTypography
import com.baraa.masroof.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    centerValue: BigDecimal? = null,
    modifier: Modifier = Modifier,
) {
    if (slices.isEmpty()) return
    val total = remember(slices) {
        slices.fold(BigDecimal.ZERO) { acc, s -> acc + s.value }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.18f, cap = StrokeCap.Round)
                val diameter = size.minDimension
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = if (total.signum() == 0) {
                        0f
                    } else {
                        slice.value
                            .divide(total, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal(360))
                            .toFloat()
                    }
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = FinancialTypography.supportingLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (centerValue != null) {
                    val formatted = remember(centerValue) {
                        NumberFormat.getNumberInstance(Locale("ar", "SA"))
                            .apply { maximumFractionDigits = 0 }
                            .format(centerValue)
                    }
                    Text("$formatted ريال", style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            slices.forEach { slice ->
                DonutLegendRow(slice = slice, total = total)
            }
        }
    }
}

@Composable
private fun DonutLegendRow(slice: ChartSlice, total: BigDecimal) {
    val formatted = remember(slice.value) {
        NumberFormat.getNumberInstance(Locale("ar", "SA"))
            .apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }
            .format(slice.value)
    }
    val percent = if (total.signum() == 0) {
        "0%"
    } else {
        val p = slice.value.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP)
        "$p%"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = slice.color) {}
            Spacer(Modifier.width(Spacing.x2))
            Text(slice.label, style = FinancialTypography.merchant, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            "$formatted ($percent)",
            style = FinancialTypography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
