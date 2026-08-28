package com.baraa.masroof.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofSpacing
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

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
                    .padding(
                        horizontal = MasroofSpacing.appBarPadding,
                        vertical = MasroofSpacing.appBarPadding,
                    ),
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
                            size = MasroofIconSizes.logo,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(MasroofSpacing.sectionHeaderGap))
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
                .padding(horizontal = MasroofSpacing.screenHorizontal)
                .padding(top = MasroofSpacing.screenVertical)
                .padding(bottom = MasroofSpacing.screenVertical),
        )
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
