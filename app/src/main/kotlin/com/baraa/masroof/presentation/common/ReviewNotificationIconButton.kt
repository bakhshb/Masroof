package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R

@Composable
fun ReviewNotificationIconButton(
    reviewCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    // TopStart flips with locale: top-left in English (LTR), top-right in Arabic (RTL).
    val badgeOffsetX = if (layoutDirection == LayoutDirection.Rtl) (-4).dp else 4.dp

    Box(modifier = modifier.size(48.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.matchParentSize()) {
            Icon(
                imageVector = MasroofIcons.notifications,
                contentDescription = if (reviewCount > 0) {
                    stringResource(R.string.dashboard_review_notifications_count, reviewCount)
                } else {
                    stringResource(R.string.dashboard_review_notifications)
                },
            )
        }
        if (reviewCount > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = badgeOffsetX, y = 6.dp),
            ) {
                Text(
                    if (reviewCount > 99) "99+" else reviewCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
