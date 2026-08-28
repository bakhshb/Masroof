package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun MasroofSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onViewAll: (() -> Unit)? = null,
    viewAllLabel: String? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    when {
        icon != null -> {
            SectionHeader(
                title = title,
                icon = icon,
                modifier = modifier,
                onViewAll = onViewAll,
                viewAllLabel = viewAllLabel,
            )
        }

        onTrailingClick != null && trailingLabel != null -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MasroofSectionTitle(title = title)
                TextButton(onClick = onTrailingClick) {
                    Text(trailingLabel)
                }
            }
        }

        onViewAll != null && viewAllLabel != null -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MasroofSectionTitle(title = title)
                TextButton(onClick = onViewAll) {
                    Text(
                        viewAllLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        else -> {
            MasroofSectionTitle(title = title, modifier = modifier)
        }
    }
}
