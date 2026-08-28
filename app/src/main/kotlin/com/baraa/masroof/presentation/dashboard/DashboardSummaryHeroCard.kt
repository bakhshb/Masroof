package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent

data class DashboardSummaryHeroSpec(
    val accent: MasroofCardAccent,
    val primary: DashboardSummaryMetricItem,
    val secondary: List<DashboardSummaryMetricItem> = emptyList(),
    val footerHint: String? = null,
)

@Composable
fun DashboardSummaryHeroCard(
    spec: DashboardSummaryHeroSpec,
    modifier: Modifier = Modifier,
) {
    MasroofCard(modifier = modifier, accent = spec.accent) {
        DashboardSummaryPrimaryMetric(
            title = spec.primary.title,
            amount = spec.primary.amount,
            tone = spec.primary.tone,
            hint = spec.primary.hint,
            signedAmount = spec.primary.signedAmount,
            modifier = Modifier.fillMaxWidth(),
        )

        spec.footerHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (spec.secondary.isNotEmpty()) {
            DashboardSummaryCardDivider()
            DashboardSummaryMetricGrid(
                metrics = spec.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
