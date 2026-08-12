package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R

@Composable
fun ForeignCurrencyNotice(
    excludedCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconLabelRow(
                icon = MasroofIcons.periodHint,
                label = stringResource(R.string.dashboard_excluded_other_currency_title),
                iconTint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                stringResource(R.string.dashboard_excluded_other_currency, excludedCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.dashboard_excluded_other_currency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
