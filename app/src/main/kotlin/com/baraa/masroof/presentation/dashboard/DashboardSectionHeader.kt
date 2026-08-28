package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.presentation.common.MasroofSectionHeader

@Composable
fun DashboardSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onViewAll: (() -> Unit)? = null,
    viewAllLabel: String? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) = MasroofSectionHeader(
    title = title,
    modifier = modifier,
    icon = icon,
    onViewAll = onViewAll,
    viewAllLabel = viewAllLabel,
    trailingLabel = trailingLabel,
    onTrailingClick = onTrailingClick,
)
