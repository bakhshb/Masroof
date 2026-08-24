package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.DashboardSectionSize
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

data class DashboardSectionMetrics(
    val heroAmountScale: Float,
    val heroExtraPadding: Dp,
    val heroProgressHeight: Dp,
    val showHeroFooter: Boolean,
    val quickMinHeight: Dp,
    val quickPadding: Dp,
    val quickIconSize: Dp,
    val accountIconSize: Dp,
    val accountAmountScale: Float,
    val showAccountPeriodFlow: Boolean,
    val cardWidth: Dp,
    val cardMinHeight: Dp,
    val recentTransactionCount: Int,
    val transactionSpacing: Dp,
)

fun dashboardSectionMetrics(size: DashboardSectionSize): DashboardSectionMetrics =
    when (size) {
        DashboardSectionSize.SMALL -> DashboardSectionMetrics(
            heroAmountScale = 0.78f,
            heroExtraPadding = 0.dp,
            heroProgressHeight = 4.dp,
            showHeroFooter = false,
            quickMinHeight = 96.dp,
            quickPadding = 6.dp,
            quickIconSize = 22.dp,
            accountIconSize = 28.dp,
            accountAmountScale = 0.9f,
            showAccountPeriodFlow = false,
            cardWidth = 236.dp,
            cardMinHeight = 196.dp,
            recentTransactionCount = 3,
            transactionSpacing = 4.dp,
        )

        DashboardSectionSize.MEDIUM -> DashboardSectionMetrics(
            heroAmountScale = 1f,
            heroExtraPadding = 2.dp,
            heroProgressHeight = 6.dp,
            showHeroFooter = true,
            quickMinHeight = 118.dp,
            quickPadding = 10.dp,
            quickIconSize = 28.dp,
            accountIconSize = 38.dp,
            accountAmountScale = 1f,
            showAccountPeriodFlow = true,
            cardWidth = 288.dp,
            cardMinHeight = 235.dp,
            recentTransactionCount = 5,
            transactionSpacing = 6.dp,
        )

        DashboardSectionSize.LARGE -> DashboardSectionMetrics(
            heroAmountScale = 1.32f,
            heroExtraPadding = 8.dp,
            heroProgressHeight = 10.dp,
            showHeroFooter = true,
            quickMinHeight = 152.dp,
            quickPadding = 14.dp,
            quickIconSize = 36.dp,
            accountIconSize = 48.dp,
            accountAmountScale = 1.18f,
            showAccountPeriodFlow = true,
            cardWidth = 328.dp,
            cardMinHeight = 280.dp,
            recentTransactionCount = 8,
            transactionSpacing = 10.dp,
        )
    }

/** Upper bound for home recent rows — matches [DashboardSectionSize.LARGE]. */
const val DASHBOARD_RECENT_TRANSACTION_LIMIT: Int = 8

@Composable
fun Modifier.dashboardSectionFrame(
    size: DashboardSectionSize,
    editing: Boolean,
): Modifier {
    val extended = MasroofThemeExtras.extendedColors
    val padding = when (size) {
        DashboardSectionSize.SMALL -> 0.dp
        DashboardSectionSize.MEDIUM -> 2.dp
        DashboardSectionSize.LARGE -> 8.dp
    }
    val editBorder = if (editing) {
        Modifier.border(1.dp, extended.inflow.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    } else {
        Modifier
    }
    return this
        .padding(vertical = padding)
        .then(editBorder)
}

fun heroAmountStyleScale(size: DashboardSectionSize): Float =
    dashboardSectionMetrics(size).heroAmountScale

fun quickCardPadding(size: DashboardSectionSize): Dp =
    dashboardSectionMetrics(size).quickPadding
