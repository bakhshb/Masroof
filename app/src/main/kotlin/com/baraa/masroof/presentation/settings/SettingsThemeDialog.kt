package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.theme.ThemeMode

@Composable
fun SettingsThemeDialog(
    selectedMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelectMode: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_title)) },
        text = {
            Column {
                themeOption(
                    mode = ThemeMode.LIGHT,
                    label = stringResource(R.string.settings_theme_light),
                    selectedMode = selectedMode,
                    onSelectMode = onSelectMode,
                )
                themeOption(
                    mode = ThemeMode.DARK,
                    label = stringResource(R.string.settings_theme_dark),
                    selectedMode = selectedMode,
                    onSelectMode = onSelectMode,
                )
                themeOption(
                    mode = ThemeMode.SYSTEM,
                    label = stringResource(R.string.settings_theme_system),
                    selectedMode = selectedMode,
                    onSelectMode = onSelectMode,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun themeOption(
    mode: ThemeMode,
    label: String,
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = mode == selectedMode,
                onClick = { onSelectMode(mode) },
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = mode == selectedMode,
            onClick = { onSelectMode(mode) },
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}
