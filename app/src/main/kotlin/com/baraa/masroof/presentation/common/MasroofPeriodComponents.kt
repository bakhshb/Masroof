package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofPillShape
import com.baraa.masroof.presentation.theme.MasroofSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasroofPeriodPill(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousContentDescription: String? = null,
    nextContentDescription: String? = null,
    onCustomize: (() -> Unit)? = null,
    customizeLabel: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MasroofPillShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MasroofSpacing.appBarPadding,
                    vertical = MasroofSpacing.appBarPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = MasroofIcons.periodPrevious,
                        contentDescription = previousContentDescription,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = MasroofIcons.calendar,
                        contentDescription = null,
                        modifier = Modifier.size(MasroofIconSizes.periodInline),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.dashboard_salary_period_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = MasroofIcons.periodNext,
                        contentDescription = nextContentDescription,
                    )
                }
            }
            if (onCustomize != null && customizeLabel != null) {
                Surface(
                    onClick = onCustomize,
                    shape = RoundedCornerShape(MasroofSpacing.customizeButtonRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = MasroofSpacing.rowHorizontalPadding,
                            vertical = MasroofSpacing.customizeButtonVerticalPadding,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.inlineGap),
                    ) {
                        Icon(
                            imageVector = MasroofIcons.customize,
                            contentDescription = null,
                            modifier = Modifier.size(MasroofIconSizes.xs),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            customizeLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MasroofPeriodDisplay(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MasroofPillShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MasroofSpacing.periodDisplayHorizontalPadding,
                    vertical = MasroofSpacing.periodDisplayVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = MasroofIcons.calendar,
                contentDescription = null,
                modifier = Modifier.size(MasroofIconSizes.periodInline),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.dashboard_salary_period_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
