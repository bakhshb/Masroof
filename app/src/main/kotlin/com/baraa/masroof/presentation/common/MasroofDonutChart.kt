package com.baraa.masroof.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.RoundingMode

@Composable
fun MasroofDonutChart(
    paidFraction: Float,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = MasroofSpacing.donutChartSize,
) {
    val extended = MasroofThemeExtras.extendedColors
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val paidColor = extended.inflow
    val remainingColor = extended.liability
    val clampedPaid = paidFraction.coerceIn(0f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = size.toPx() * 0.14f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            if (clampedPaid > 0f) {
                drawArc(
                    color = paidColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clampedPaid,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }
            if (clampedPaid < 1f) {
                drawArc(
                    color = remainingColor,
                    startAngle = -90f + (360f * clampedPaid),
                    sweepAngle = 360f * (1f - clampedPaid),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                centerValue,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun commitmentDonutPaidFraction(total: java.math.BigDecimal, paid: java.math.BigDecimal): Float {
    if (total.signum() <= 0) return 0f
    return paid.divide(total, 4, RoundingMode.HALF_EVEN).toFloat().coerceIn(0f, 1f)
}
