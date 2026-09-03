package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.baraa.masroof.presentation.theme.MasroofShapes
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.BigDecimal
import java.math.RoundingMode

enum class MasroofHorizontalBarStyle {
    Default,
    Inflow,
    Outflow,
}

object MasroofBarChart {
    fun progress(value: BigDecimal, max: BigDecimal): Float {
        if (max.signum() <= 0) return 0f
        return value.max(BigDecimal.ZERO)
            .divide(max, 4, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

@Composable
fun MasroofHorizontalBar(
    progress: Float,
    modifier: Modifier = Modifier,
    style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default,
) {
    val extended = MasroofThemeExtras.extendedColors
    val fillColor = when (style) {
        MasroofHorizontalBarStyle.Default -> MaterialTheme.colorScheme.primary
        MasroofHorizontalBarStyle.Inflow -> extended.inflow
        MasroofHorizontalBarStyle.Outflow -> extended.outflow
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MasroofSpacing.accentBarHeight)
            .background(trackColor, MasroofShapes.small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(MasroofSpacing.accentBarHeight)
                .background(fillColor, MasroofShapes.small),
        )
    }
}

@Composable
fun MasroofRankedBarRow(
    title: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    style: MasroofHorizontalBarStyle = MasroofHorizontalBarStyle.Default,
    onClick: (() -> Unit)? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val valueColor = when (style) {
        MasroofHorizontalBarStyle.Default -> MaterialTheme.colorScheme.primary
        MasroofHorizontalBarStyle.Inflow -> extended.inflow
        MasroofHorizontalBarStyle.Outflow -> extended.outflow
    }

    MasroofCard(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = valueColor,
                )
            }
            MasroofHorizontalBar(
                progress = progress,
                style = style,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
