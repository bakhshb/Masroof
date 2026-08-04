package com.baraa.masroof.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.diagnostics.DeveloperPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onCategories: () -> Unit,
    onMerchants: () -> Unit,
    onAccounts: () -> Unit,
    onAccountLinks: () -> Unit = {},
    onFinancialHistory: () -> Unit = {},
    onAccountLinkRules: () -> Unit = {},
    onAi: () -> Unit,
    onAiSuggestions: () -> Unit,
    onAiBatch: () -> Unit,
    onDiagnostics: () -> Unit = {},
    onTestData: () -> Unit = {},
    onReleaseNotes: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val devPrefs: DeveloperPreferences = app.developerPreferences

    var showDevDetails by remember { mutableStateOf(devPrefs.showDevDetails) }
    var testDataMode by remember { mutableStateOf(devPrefs.testDataMode) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inner)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsRow(
                title = stringResource(id = R.string.settings_categories),
                icon = Icons.Filled.Add,
                onClick = onCategories,
            )
            SettingsRow(
                title = stringResource(id = R.string.settings_merchants),
                icon = Icons.Filled.Add,
                onClick = onMerchants,
            )
            SettingsRow(
                title = stringResource(id = R.string.ai_settings_title),
                icon = Icons.Filled.Settings,
                onClick = onAi,
            )
            SettingsRow(
                title = stringResource(id = R.string.ai_suggestions_title),
                icon = Icons.Filled.Inbox,
                onClick = onAiSuggestions,
            )
            SettingsRow(
                title = stringResource(id = R.string.ai_batch_action),
                icon = Icons.Filled.Inbox,
                onClick = onAiBatch,
            )
            SettingsRow(
                title = stringResource(id = R.string.settings_accounts_short),
                icon = Icons.Filled.AccountBox,
                onClick = onAccounts,
            )
            SettingsRow(
                title = "ربط العمليات بالحسابات",
                icon = Icons.Filled.AccountBox,
                onClick = onAccountLinks,
            )
            SettingsRow(
                title = "السجل المالي",
                icon = Icons.Filled.Info,
                onClick = onFinancialHistory,
            )
            SettingsRow(
                title = "قواعد الربط المحفوظة",
                icon = Icons.Filled.AccountBox,
                onClick = onAccountLinkRules,
            )

            HorizontalDivider()

            SettingsRow(
                title = stringResource(id = R.string.diagnostics_title),
                icon = Icons.Filled.Info,
                onClick = onDiagnostics,
            )
            SettingsRow(
                title = stringResource(id = R.string.test_data_label),
                icon = Icons.Filled.Inbox,
                onClick = onTestData,
            )
            SettingsRow(
                title = stringResource(id = R.string.release_notes_title),
                icon = Icons.Filled.Info,
                onClick = onReleaseNotes,
            )

            // Developer toggles.
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.diagnostics_show_dev_details),
                checked = showDevDetails,
                onCheckedChange = {
                    showDevDetails = it
                    devPrefs.showDevDetails = it
                },
            )
            SwitchRow(
                title = stringResource(R.string.diagnostics_test_data_mode),
                checked = testDataMode,
                onCheckedChange = {
                    testDataMode = it
                    devPrefs.testDataMode = it
                },
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}