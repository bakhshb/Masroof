package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.MasroofBadgeShape
import com.baraa.masroof.presentation.theme.MasroofCardShape
import com.baraa.masroof.presentation.theme.MasroofPillShape
import com.baraa.masroof.presentation.theme.MasroofRowShape
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

enum class MasroofCardAccent {
    None,
    Account,
    Credit,
    Liability,
    Inflow,
    Outflow,
}

enum class MasroofMoneyRowStyle {
    Inflow,
    Outflow,
    Highlight,
    Neutral,
}

@Composable
fun MasroofScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        extended.backgroundGradientTop,
                        background,
                        background,
                    ),
                    startY = 0f,
                    endY = 360f,
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun MasroofAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    if (onBack != null) {
                        BackNavigationIcon(
                            onClick = onBack,
                            contentDescription = backContentDescription,
                        )
                    }
                    if (showLogo) {
                        MasroofLogo(
                            size = 28.dp,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun MasroofCard(
    modifier: Modifier = Modifier,
    accent: MasroofCardAccent = MasroofCardAccent.None,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    val accentColor = when (accent) {
        MasroofCardAccent.None -> Color.Transparent
        MasroofCardAccent.Account -> extended.account
        MasroofCardAccent.Credit -> extended.card
        MasroofCardAccent.Liability -> extended.liability
        MasroofCardAccent.Inflow -> extended.inflow
        MasroofCardAccent.Outflow -> extended.outflow
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = MasroofCardShape,
                ambientColor = extended.cardShadow,
                spotColor = extended.cardShadow,
            ),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (accent != MasroofCardAccent.None) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(accentColor),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                content = content,
            )
        }
    }
}

@Composable
fun MasroofBadge(
    text: String,
    accent: MasroofCardAccent,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    val (background, foreground) = when (accent) {
        MasroofCardAccent.Account -> extended.accountSoft to extended.account
        MasroofCardAccent.Credit -> extended.cardSoft to extended.card
        MasroofCardAccent.Liability -> extended.liabilitySoft to extended.liability
        MasroofCardAccent.Inflow -> extended.inflowSoft to extended.inflow
        MasroofCardAccent.Outflow -> extended.outflowSoft to extended.outflow
        MasroofCardAccent.None -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MasroofBadgeShape,
        color = background,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MasroofMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitle: String? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier,
        shape = MasroofCardShape,
        color = extended.miniBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MasroofMoneyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: MasroofMoneyRowStyle = MasroofMoneyRowStyle.Neutral,
    leadingIcon: ImageVector? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    val (background, borderColor) = when (style) {
        MasroofMoneyRowStyle.Inflow -> extended.inflowSoft to extended.inflowRowBorder
        MasroofMoneyRowStyle.Outflow -> extended.outflowSoft to extended.outflowRowBorder
        MasroofMoneyRowStyle.Highlight -> extended.highlight to extended.highlightBorder
        MasroofMoneyRowStyle.Neutral -> extended.miniBackground to extended.cardBorder
    }
    val valueColor = when (style) {
        MasroofMoneyRowStyle.Inflow -> extended.inflow
        MasroofMoneyRowStyle.Outflow -> extended.outflow
        MasroofMoneyRowStyle.Highlight -> extended.outflow
        MasroofMoneyRowStyle.Neutral -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MasroofRowShape)
            .background(background)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, MasroofRowShape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = valueColor,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
    }
}

@Composable
fun MasroofHintBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MasroofRowShape,
        color = extended.hintBackground,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(com.baraa.masroof.R.string.dashboard_salary_period_short),
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
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = MasroofIcons.customize,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = MasroofIcons.calendar,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(com.baraa.masroof.R.string.dashboard_salary_period_short),
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

@Composable
fun MasroofSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun MasroofCycleChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    Surface(
        modifier = modifier,
        shape = MasroofBadgeShape,
        color = extended.cardSoft,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = extended.card,
        )
    }
}

@Composable
fun MasroofSecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MasroofAppBar(
            title = title,
            showLogo = false,
            onBack = onBack,
            backContentDescription = backContentDescription,
            actions = actions,
        )
        content(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun MasroofNavCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extended = MasroofThemeExtras.extendedColors
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = MasroofCardShape,
                ambientColor = extended.cardShadow,
                spotColor = extended.cardShadow,
            ),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.cardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            content = content,
        )
    }
}
