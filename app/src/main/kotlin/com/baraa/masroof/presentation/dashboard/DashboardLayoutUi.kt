package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.DashboardSectionSize
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun Modifier.dashboardSectionFrame(
    size: DashboardSectionSize,
    editing: Boolean,
): Modifier {
    val extended = MasroofThemeExtras.extendedColors
    val padding = when (size) {
        DashboardSectionSize.SMALL -> 0.dp
        DashboardSectionSize.MEDIUM -> 0.dp
        DashboardSectionSize.LARGE -> 2.dp
    }
    val editBorder = if (editing) {
        Modifier.border(1.dp, extended.inflow.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
    } else {
        Modifier
    }
    return this
        .padding(vertical = padding)
        .then(editBorder)
}

fun heroAmountStyleScale(size: DashboardSectionSize): Float =
    when (size) {
        DashboardSectionSize.SMALL -> 0.85f
        DashboardSectionSize.MEDIUM -> 1f
        DashboardSectionSize.LARGE -> 1.12f
    }

fun quickCardPadding(size: DashboardSectionSize): androidx.compose.ui.unit.Dp =
    when (size) {
        DashboardSectionSize.SMALL -> 8.dp
        DashboardSectionSize.MEDIUM -> 10.dp
        DashboardSectionSize.LARGE -> 12.dp
    }
