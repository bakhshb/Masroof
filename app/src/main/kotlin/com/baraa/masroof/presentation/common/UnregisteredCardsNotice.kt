package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R

@Composable
fun UnregisteredCardsNotice(
    firstLast4: String,
    extraCount: Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLast4 = formatCardLast4(firstLast4)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.cardPayment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.ownership_prompt_banner_title, displayLast4),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                if (extraCount > 0) {
                    stringResource(R.string.dashboard_unregistered_cards_more, extraCount)
                } else {
                    stringResource(R.string.dashboard_unregistered_cards_body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconTextButtonOutlined(
                onClick = onOpenSettings,
                icon = MasroofIcons.settings,
                text = stringResource(R.string.dashboard_manage_cards_in_settings),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
