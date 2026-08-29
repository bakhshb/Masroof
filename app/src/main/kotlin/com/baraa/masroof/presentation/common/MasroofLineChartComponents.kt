package com.baraa.masroof.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.MasroofBadgeShape
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

internal object MasroofLineChartLayout {
    fun xForPoint(index: Int, width: Float, pointCount: Int): Float {
        if (pointCount <= 1) return 0f
        return index * (width / (pointCount - 1))
    }

    fun yFor(
        value: BigDecimal,
        range: MasroofLineChart.ValueRange,
        top: Float,
        chartHeight: Float,
    ): Float {
        val span = range.span.toFloat()
        val fraction = value.subtract(range.minimum).toFloat() / span
        return (top + chartHeight) - (fraction * chartHeight)
    }

    fun tooltipLeft(
        anchorX: Float,
        tooltipWidth: Float,
        chartWidth: Float,
        horizontalPadding: Float,
    ): Float {
        if (tooltipWidth <= 0f) return anchorX
        val maxLeft = (chartWidth - tooltipWidth - horizontalPadding).coerceAtLeast(horizontalPadding)
        return (anchorX - tooltipWidth / 2f).coerceIn(horizontalPadding, maxLeft)
    }

    fun coerceSelectedIndex(index: Int?, pointCount: Int): Int? {
        if (pointCount <= 0) return null
        return index?.takeIf { it in 0 until pointCount }
    }
}

@Composable
fun MasroofLineChart(
    values: List<BigDecimal>,
    referenceValue: BigDecimal,
    modifier: Modifier = Modifier,
    selectedPointIndex: Int? = null,
    onPointSelected: ((Int) -> Unit)? = null,
    pointLabel: (Int) -> String = { "" },
) {
    MasroofInteractiveLineChart(
        values = values,
        referenceValue = referenceValue,
        modifier = modifier,
        selectedPointIndex = selectedPointIndex,
        onPointSelected = onPointSelected,
        pointLabel = pointLabel,
    )
}

@Composable
fun MasroofInteractiveLineChart(
    values: List<BigDecimal>,
    referenceValue: BigDecimal,
    modifier: Modifier = Modifier,
    selectedPointIndex: Int? = null,
    onPointSelected: ((Int) -> Unit)? = null,
    pointLabel: (Int) -> String = { "" },
) {
    if (values.isEmpty()) return

    val resolvedSelectedIndex = MasroofLineChartLayout.coerceSelectedIndex(selectedPointIndex, values.size)
    val extended = MasroofThemeExtras.extendedColors
    val range = remember(values, referenceValue) {
        MasroofLineChart.valueRange(values, referenceValue)
    }
    val lineColor = extended.outflow
    val referenceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedGuideColor = MaterialTheme.colorScheme.outline
    val selectedPointHaloColor = MaterialTheme.colorScheme.surface
    val lineWidth = MasroofSpacing.chartLineWidth
    val pointRadius = MasroofSpacing.chartPointRadius
    val verticalPadding = MasroofSpacing.chartVerticalPadding
    val tooltipGap = MasroofSpacing.sectionHeaderGap
    val tooltipHorizontalPadding = MasroofSpacing.inlineGap
    val density = LocalDensity.current
    val tooltipGapPx = with(density) { tooltipGap.toPx() }
    val tooltipHorizontalPaddingPx = with(density) { tooltipHorizontalPadding.toPx() }
    val previousPointLabel = stringResource(R.string.chart_previous_point)
    val nextPointLabel = stringResource(R.string.chart_next_point)

    val selectedLabel = resolvedSelectedIndex?.let(pointLabel).orEmpty()
    val chartSemantics = Modifier.semantics {
        if (selectedLabel.isNotBlank()) {
            contentDescription = selectedLabel
        }
        if (onPointSelected != null) {
            customActions = buildList {
                if (resolvedSelectedIndex != null && resolvedSelectedIndex > 0) {
                    add(
                        CustomAccessibilityAction(previousPointLabel) {
                            onPointSelected(resolvedSelectedIndex - 1)
                            true
                        },
                    )
                }
                if (resolvedSelectedIndex != null && resolvedSelectedIndex < values.lastIndex) {
                    add(
                        CustomAccessibilityAction(nextPointLabel) {
                            onPointSelected(resolvedSelectedIndex + 1)
                            true
                        },
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MasroofSpacing.chartHeight)
            .then(chartSemantics)
            .pointerInput(values.size, onPointSelected) {
                val onSelect = onPointSelected ?: return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    fun selectAt(x: Float) {
                        MasroofLineChart.nearestPointIndex(
                            x = x,
                            width = size.width.toFloat(),
                            pointCount = values.size,
                        )?.let(onSelect)
                    }
                    var scrubbing: Boolean? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            if (scrubbing != true) {
                                val movement = (change.position - down.position).getDistance()
                                if (movement < touchSlop) {
                                    selectAt(down.position.x)
                                    change.consume()
                                }
                            }
                            break
                        }
                        val movement = change.position - down.position
                        if (scrubbing == null && movement.getDistance() >= touchSlop) {
                            scrubbing = kotlin.math.abs(movement.x) > kotlin.math.abs(movement.y)
                            if (scrubbing == false) break
                        }
                        if (scrubbing == true) {
                            selectAt(change.position.x)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val chartWidthPx = constraints.maxWidth.toFloat()
        val chartHeightPx = with(density) { MasroofSpacing.chartHeight.toPx() }
        val topPx = with(density) { verticalPadding.toPx() }
        val bottomPx = chartHeightPx - topPx
        val drawableHeightPx = (bottomPx - topPx).coerceAtLeast(0f)

        fun pointOffset(index: Int): Offset {
            val x = MasroofLineChartLayout.xForPoint(index, chartWidthPx, values.size)
            val y = MasroofLineChartLayout.yFor(values[index], range, topPx, drawableHeightPx)
            return Offset(x, y)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val xStep = if (values.size > 1) size.width / (values.size - 1) else 0f
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = index * xStep
                        val y = MasroofLineChartLayout.yFor(value, range, topPx, drawableHeightPx)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val referenceY = MasroofLineChartLayout.yFor(
                        referenceValue,
                        range,
                        topPx,
                        drawableHeightPx,
                    )
                    val dashEffect = PathEffect.dashPathEffect(
                        floatArrayOf(lineWidth.toPx() * 2, lineWidth.toPx() * 2),
                    )
                    onDrawBehind {
                        drawLine(
                            color = referenceColor,
                            start = Offset(0f, referenceY),
                            end = Offset(size.width, referenceY),
                            strokeWidth = lineWidth.toPx(),
                            pathEffect = dashEffect,
                        )
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round),
                        )
                        values.forEachIndexed { index, value ->
                            if (index == resolvedSelectedIndex) return@forEachIndexed
                            drawCircle(
                                color = lineColor,
                                radius = pointRadius.toPx(),
                                center = Offset(index * xStep, MasroofLineChartLayout.yFor(value, range, topPx, drawableHeightPx)),
                            )
                        }
                    }
                },
        ) {
            resolvedSelectedIndex?.let { index ->
                val anchor = pointOffset(index)
                drawLine(
                    color = selectedGuideColor,
                    start = Offset(anchor.x, topPx),
                    end = Offset(anchor.x, bottomPx),
                    strokeWidth = lineWidth.toPx(),
                )
                drawCircle(
                    color = lineColor,
                    radius = pointRadius.toPx() * 2,
                    center = anchor,
                )
                drawCircle(
                    color = selectedPointHaloColor,
                    radius = pointRadius.toPx(),
                    center = anchor,
                )
            }
        }

        resolvedSelectedIndex?.let { index ->
            val anchor = pointOffset(index)
            var tooltipSize by remember(index) { mutableStateOf(IntSize.Zero) }
            val tooltipLeftPx = MasroofLineChartLayout.tooltipLeft(
                anchorX = anchor.x,
                tooltipWidth = tooltipSize.width.toFloat(),
                chartWidth = chartWidthPx,
                horizontalPadding = tooltipHorizontalPaddingPx,
            )
            val tooltipTopPx = (anchor.y - tooltipGapPx - tooltipSize.height).coerceAtLeast(0f)
            Surface(
                modifier = Modifier
                    .onSizeChanged { tooltipSize = it }
                    .offset {
                        IntOffset(
                            tooltipLeftPx.roundToInt(),
                            tooltipTopPx.roundToInt(),
                        )
                    },
                shape = MasroofBadgeShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = pointLabel(index),
                    modifier = Modifier.padding(
                        horizontal = MasroofSpacing.badgeHorizontalPadding,
                        vertical = MasroofSpacing.badgeVerticalPadding,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}
