package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofPeriodDisplay
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@Composable
fun DashboardSummaryScaffold(
    title: String,
    state: DashboardUiState,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    val periodRangeLabel = state.period?.let {
        FinancialPeriodUiFormatter.formatRange(context, it)
    } ?: state.periodLabel

    MasroofSecondaryScaffold(
        title = title,
        onBack = onBack,
        backContentDescription = stringResource(R.string.dashboard_flow_detail_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DashboardSpacing.sectionGap),
        ) {
            MasroofPeriodDisplay(label = periodRangeLabel)

            state.periodAdjustmentHint?.let { hint ->
                androidx.compose.material3.Text(
                    hint,
                    modifier = Modifier.fillMaxWidth(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.loading && state.currentAccount == null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else {
                content(Modifier.fillMaxWidth())
            }
        }
    }
}
