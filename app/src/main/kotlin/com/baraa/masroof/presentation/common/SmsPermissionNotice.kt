package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.theme.MasroofThemeExtras

@Composable
fun SmsPermissionNotice(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extended = MasroofThemeExtras.extendedColors
    MasroofCard(
        modifier = modifier.fillMaxWidth(),
        accent = MasroofCardAccent.Liability,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MasroofIcons.error,
                    contentDescription = null,
                    tint = extended.outflow,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.dashboard_sms_permission_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                stringResource(R.string.dashboard_sms_permission_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconTextButton(
                onClick = onRequestPermission,
                icon = MasroofIcons.rescan,
                text = stringResource(R.string.dashboard_sms_permission_grant),
                modifier = Modifier.fillMaxWidth(),
            )
            IconTextButtonOutlined(
                onClick = onOpenAppSettings,
                icon = MasroofIcons.settings,
                text = stringResource(R.string.onboarding_open_settings),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
