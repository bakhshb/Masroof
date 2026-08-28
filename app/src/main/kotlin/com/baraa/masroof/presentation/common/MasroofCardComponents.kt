package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.presentation.theme.MasroofBadgeShape
import com.baraa.masroof.presentation.theme.MasroofCardShape
import com.baraa.masroof.presentation.theme.MasroofElevation
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

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
                elevation = MasroofElevation.card,
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
                        .height(MasroofSpacing.accentBarHeight)
                        .background(accentColor),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MasroofSpacing.cardInnerPadding),
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
            modifier = Modifier.padding(
                horizontal = MasroofSpacing.badgeHorizontalPadding,
                vertical = MasroofSpacing.badgeVerticalPadding,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MasroofCompactCard(
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = MasroofSpacing.compactCardContentPadding,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val extended = MasroofThemeExtras.extendedColors
    Card(
        modifier = modifier
            .heightIn(min = MasroofSpacing.compactCardMinHeight)
            .then(
                if (clickable && onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .shadow(
                elevation = MasroofElevation.card,
                shape = MasroofCardShape,
                ambientColor = extended.cardShadow,
                spotColor = extended.cardShadow,
            ),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.navCardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = contentPadding + 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MasroofSpacing.compactCardChevronHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (clickable) {
                    Icon(
                        imageVector = MasroofIcons.periodNext,
                        contentDescription = null,
                        modifier = Modifier.size(MasroofIconSizes.compactCardChevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(MasroofIconSizes.logo)
                    .clip(RoundedCornerShape(MasroofSpacing.compactCardIconContainerRadius))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(MasroofIconSizes.compactCardLeading),
                    tint = iconTint,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MasroofSpacing.compactCardLabelTop),
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MasroofSpacing.compactCardValueTop),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                elevation = MasroofElevation.navCard,
                shape = MasroofCardShape,
                ambientColor = extended.cardShadow,
                spotColor = extended.cardShadow,
            ),
        shape = MasroofCardShape,
        colors = CardDefaults.cardColors(
            containerColor = extended.navCardSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, extended.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MasroofSpacing.navCardHorizontalPadding,
                vertical = MasroofSpacing.navCardVerticalPadding,
            ),
            content = content,
        )
    }
}
