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
fun CardOwnershipPromptBanner(
    last4: String,
    extraCount: Int,
    enabled: Boolean,
    onConfirmOwned: () -> Unit,
    onMarkExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLast4 = formatCardLast4(last4)
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
                stringResource(R.string.ownership_prompt_banner_question),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (extraCount > 0) {
                    stringResource(R.string.ownership_prompt_banner_body_more, extraCount)
                } else {
                    stringResource(R.string.ownership_prompt_banner_body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CardOwnershipActionRow(
                enabled = enabled,
                onConfirmOwned = onConfirmOwned,
                onMarkExternal = onMarkExternal,
            )
        }
    }
}

@Composable
fun CardOwnershipInlinePrompt(
    enabled: Boolean,
    onConfirmOwned: () -> Unit,
    onMarkExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MasroofIcons.warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.ownership_prompt_needs_confirm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        CardOwnershipActionRow(
            enabled = enabled,
            onConfirmOwned = onConfirmOwned,
            onMarkExternal = onMarkExternal,
        )
    }
}

@Composable
private fun CardOwnershipActionRow(
    enabled: Boolean,
    onConfirmOwned: () -> Unit,
    onMarkExternal: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTextButton(
            onClick = onConfirmOwned,
            enabled = enabled,
            icon = MasroofIcons.success,
            text = stringResource(R.string.onboarding_is_mine_card),
        )
        IconTextButton(
            onClick = onMarkExternal,
            enabled = enabled,
            icon = MasroofIcons.warning,
            text = stringResource(R.string.ownership_action_not_mine),
        )
    }
}
