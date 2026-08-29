package com.baraa.masroof.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.BigDecimal
import kotlin.math.roundToInt

object MasroofLineChart {
    data class ValueRange(
        val minimum: BigDecimal,
        val maximum: BigDecimal,
    ) {
        val span: BigDecimal
            get() = maximum.subtract(minimum)
    }

    fun valueRange(values: List<BigDecimal>, reference: BigDecimal): ValueRange {
        val allValues = values + reference + BigDecimal.ZERO
        val minimum = allValues.minOrNull() ?: BigDecimal.ZERO
        val maximum = allValues.maxOrNull() ?: BigDecimal.ZERO
        return if (minimum == maximum) {
            ValueRange(BigDecimal.ZERO, BigDecimal.ONE)
        } else {
            ValueRange(minimum, maximum)
        }
    }

    fun nearestPointIndex(x: Float, width: Float, pointCount: Int): Int? {
        if (pointCount <= 0 || width <= 0f) return null
        if (pointCount == 1) return 0
        val step = width / (pointCount - 1)
        return (x / step).roundToInt().coerceIn(0, pointCount - 1)
    }
}

@Composable
fun MasroofLineChart(
    values: List<BigDecimal>,
    referenceValue: BigDecimal,
    modifier: Modifier = Modifier,
    selectedPointIndex: Int? = null,
    onPointSelected: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return

    val extended = MasroofThemeExtras.extendedColors
    val range = MasroofLineChart.valueRange(values, referenceValue)
    val lineColor = extended.outflow
    val referenceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineWidth = MasroofSpacing.chartLineWidth
    val pointRadius = MasroofSpacing.chartPointRadius
    val verticalPadding = MasroofSpacing.chartVerticalPadding

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MasroofSpacing.chartHeight)
            .pointerInput(values.size, onPointSelected) {
                detectTapGestures { offset ->
                    MasroofLineChart.nearestPointIndex(
                        x = offset.x,
                        width = size.width.toFloat(),
                        pointCount = values.size,
                    )?.let { index -> onPointSelected?.invoke(index) }
                }
            },
    ) {
        val top = verticalPadding.toPx()
        val bottom = size.height - verticalPadding.toPx()
        val chartHeight = (bottom - top).coerceAtLeast(0f)
        val span = range.span.toFloat()
        fun yFor(value: BigDecimal): Float {
            val fraction = value.subtract(range.minimum).toFloat() / span
            return bottom - (fraction * chartHeight)
        }

        val referenceY = yFor(referenceValue)
        drawLine(
            color = referenceColor,
            start = androidx.compose.ui.geometry.Offset(0f, referenceY),
            end = androidx.compose.ui.geometry.Offset(size.width, referenceY),
            strokeWidth = lineWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(lineWidth.toPx() * 2, lineWidth.toPx() * 2)),
        )

        val xStep = if (values.size > 1) size.width / (values.size - 1) else 0f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * xStep
            val y = yFor(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round),
        )
        values.forEachIndexed { index, value ->
            drawCircle(
                color = lineColor,
                radius = if (index == selectedPointIndex) pointRadius.toPx() * 2 else pointRadius.toPx(),
                center = androidx.compose.ui.geometry.Offset(index * xStep, yFor(value)),
            )
        }
    }
}
