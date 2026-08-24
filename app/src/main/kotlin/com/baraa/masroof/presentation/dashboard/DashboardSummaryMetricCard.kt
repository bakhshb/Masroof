package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.baraa.masroof.application.dashboard.SignedMoneyAmount
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.theme.MasroofThemeExtras
import java.math.BigDecimal

enum class DashboardMetricTone {
    Inflow,
    Outflow,
    Liability,
    Signed,
    Neutral,
}

data class DashboardSummaryMetricItem(
    val title: String,
    val amount: String,
    val tone: DashboardMetricTone,
    val hint: String? = null,
    val signedAmount: BigDecimal? = null,
)

@Composable
fun DashboardSummaryMetricCard(
    title: String,
    amount: String,
    tone: DashboardMetricTone,
    modifier: Modifier = Modifier,
    accent: MasroofCardAccent = MasroofCardAccent.None,
    hint: String? = null,
    signedAmount: BigDecimal? = null,
) {
    DashboardSummaryMetricsCard(
        metrics = listOf(
            DashboardSummaryMetricItem(
                title = title,
                amount = amount,
                tone = tone,
                hint = hint,
                signedAmount = signedAmount,
            ),
        ),
        modifier = modifier,
        accent = accent,
    )
}

@Composable
fun DashboardSummaryMetricsCard(
    metrics: List<DashboardSummaryMetricItem>,
    modifier: Modifier = Modifier,
    accent: MasroofCardAccent = MasroofCardAccent.None,
) {
    if (metrics.isEmpty()) return

    MasroofCard(modifier = modifier, accent = accent) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            DashboardSummaryMetricBlock(
                title = metric.title,
                amount = metric.amount,
                tone = metric.tone,
                hint = metric.hint,
                signedAmount = metric.signedAmount,
            )
        }
    }
}

@Composable
private fun DashboardSummaryMetricBlock(
    title: String,
    amount: String,
    tone: DashboardMetricTone,
    hint: String?,
    signedAmount: BigDecimal?,
) {
    DashboardSummaryPrimaryMetric(
        title = title,
        amount = amount,
        tone = tone,
        hint = hint,
        signedAmount = signedAmount,
    )
}

@Composable
fun resolveMetricToneColor(tone: DashboardMetricTone, signedAmount: BigDecimal? = null): Color {
    val extended = MasroofThemeExtras.extendedColors
    return when (tone) {
        DashboardMetricTone.Inflow -> extended.inflow
        DashboardMetricTone.Outflow -> extended.outflow
        DashboardMetricTone.Liability -> extended.liability
        DashboardMetricTone.Signed -> signedAmount?.let { signedAmountMetricTone(it) }
            ?.let { resolveMetricToneColor(it) }
            ?: MaterialTheme.colorScheme.onSurface
        DashboardMetricTone.Neutral -> MaterialTheme.colorScheme.onSurface
    }
}

fun signedMoneyMetricTone(amount: SignedMoneyAmount): DashboardMetricTone =
    when (amount.amount.signum()) {
        1 -> DashboardMetricTone.Inflow
        -1 -> DashboardMetricTone.Outflow
        else -> DashboardMetricTone.Neutral
    }

fun spendingMetricTone(amount: SignedMoneyAmount): DashboardMetricTone =
    when (amount.amount.signum()) {
        1 -> DashboardMetricTone.Outflow
        -1 -> DashboardMetricTone.Inflow
        else -> DashboardMetricTone.Neutral
    }

fun signedAmountMetricTone(amount: BigDecimal): DashboardMetricTone =
    when (amount.signum()) {
        1 -> DashboardMetricTone.Inflow
        -1 -> DashboardMetricTone.Outflow
        else -> DashboardMetricTone.Neutral
    }
