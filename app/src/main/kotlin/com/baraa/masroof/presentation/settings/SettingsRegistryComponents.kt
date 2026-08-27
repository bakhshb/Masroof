package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofCardAccent
import com.baraa.masroof.presentation.common.MasroofIcons

/**
 * Registry row for Arabic RTL: identity on the right (start), action on the left (end).
 */
@Composable
fun SettingsRegistryItemCard(
    icon: ImageVector,
    bank: Bank,
    title: String,
    modifier: Modifier = Modifier,
    showBankLabel: Boolean = true,
    endAction: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    MasroofCard(
        modifier = modifier.fillMaxWidth(),
        accent = if (icon == MasroofIcons.cardPayment) {
            MasroofCardAccent.Credit
        } else {
            MasroofCardAccent.Account
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (endAction != null) 92.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    if (showBankLabel) {
                        Text(
                            settingsBankLabel(bank),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            endAction?.let { action ->
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    action()
                }
            }
        }
        footer?.invoke()
    }
}

@Composable
fun SettingsStopTrackingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String,
) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        contentPadding = ButtonDefaults.ContentPadding,
        border = BorderStroke(1.dp, error.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = error,
            disabledContentColor = error.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = MasroofIcons.warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.settings_action_stop_short),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SettingsResumeTrackingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            imageVector = MasroofIcons.success,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.settings_action_resume_short),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun settingsBankLabel(bank: Bank): String =
    if (bank == Bank.BANK_ALJAZIRA) {
        stringResource(R.string.bank_aljazira)
    } else {
        stringResource(R.string.bank_unknown)
    }
