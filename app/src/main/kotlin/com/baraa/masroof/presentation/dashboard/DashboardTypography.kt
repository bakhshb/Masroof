package com.baraa.masroof.presentation.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

enum class DashboardAmountRole {
    Hero,
    Card,
    Tile,
    List,
}

object DashboardTextStyles {
    val heroAmount: TextStyle
        @Composable get() = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)

    val cardAmount: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    val tileAmount: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)

    val listAmount: TextStyle
        @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)

    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)

    val hint: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    val breakdownLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium

    val breakdownTotal: TextStyle
        @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    val metricTileLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    val metricTileAmount: TextStyle
        @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
}

@Composable
fun DashboardAmountText(
    amount: String,
    role: DashboardAmountRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    heroScale: Float = 1f,
) {
    val baseStyle = when (role) {
        DashboardAmountRole.Hero -> DashboardTextStyles.heroAmount.copy(
            fontSize = DashboardTextStyles.heroAmount.fontSize * heroScale,
        )
        DashboardAmountRole.Card -> DashboardTextStyles.cardAmount
        DashboardAmountRole.Tile -> DashboardTextStyles.tileAmount
        DashboardAmountRole.List -> DashboardTextStyles.listAmount
    }
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Text(
        amount,
        modifier = modifier,
        style = baseStyle.copy(color = resolvedColor),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
