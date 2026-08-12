package com.baraa.masroof.presentation.common

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R

@Composable
fun ReviewNotificationIconButton(
    reviewCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BadgedBox(
        modifier = modifier,
        badge = {
            if (reviewCount > 0) {
                Badge {
                    Text(
                        if (reviewCount > 99) "99+" else reviewCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = MasroofIcons.notifications,
                contentDescription = if (reviewCount > 0) {
                    stringResource(R.string.dashboard_review_notifications_count, reviewCount)
                } else {
                    stringResource(R.string.dashboard_review_notifications)
                },
            )
        }
    }
}
