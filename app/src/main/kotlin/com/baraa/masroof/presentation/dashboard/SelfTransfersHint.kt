package com.baraa.masroof.presentation.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R

enum class SelfTransfersHintStyle {
    NeutralExcluded,
    IncludedInTotals,
}

@Composable
fun SelfTransfersHint(
    style: SelfTransfersHintStyle,
    modifier: Modifier = Modifier,
) {
    val textRes = when (style) {
        SelfTransfersHintStyle.NeutralExcluded -> R.string.dashboard_self_transfers_neutral_hint
        SelfTransfersHintStyle.IncludedInTotals -> R.string.dashboard_self_transfers_included_hint
    }
    Text(
        stringResource(textRes),
        modifier = modifier,
        style = DashboardTextStyles.hint,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
