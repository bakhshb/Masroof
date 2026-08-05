package com.baraa.masroof.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.baraa.masroof.ui.theme.FinancialShapes

/**
 * Settings landing screen.
 *
 * The previous SettingsScreen kept a list of empty-lambda params
 * (onCategories = {}, …) that looked interactive but never navigated.
 * This composable uses the captured [SettingsDestinations] registry so
 * every row maps to a known route; the parent [SettingsNavHost]
 * fulfills the actual navigation. Rows that haven't been implemented
 * yet are visibly disabled, dimmed, and surface a "قريباً" caption
 * (or a confirmation dialog on tap) so the user understands the
 * difference between a broken row and a planned feature.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val devPrefs: DeveloperPreferences = app.developerPreferences

    var showDevDetails by remember { mutableStateOf(devPrefs.showDevDetails) }
    var testDataMode by remember { mutableStateOf(devPrefs.testDataMode) }
    val futureNotice = remember { mutableStateOf<SettingsDestination?>(null) }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsDestinations.all
                .groupBy { it.group }
                .forEach { (group, dests) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeader(group.header)
                        dests.forEach { dest ->
                            DestinationRow(
                                destination = dest,
                                onClick = {
                                    if (dest.implemented) onNavigate(dest.route)
                                    else futureNotice.value = dest
                                },
                            )
                        }
                    }
                }

            HorizontalDivider()
            SwitchRow(title = stringResource(R.string.diagnostics_show_dev_details), checked = showDevDetails, onCheckedChange = {
                showDevDetails = it; devPrefs.showDevDetails = it
            })
            SwitchRow(title = stringResource(R.string.diagnostics_test_data_mode), checked = testDataMode, onCheckedChange = {
                testDataMode = it; devPrefs.testDataMode = it
            })
        }
    }

    futureNotice.value?.let { dest ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { futureNotice.value = null },
            title = { Text(dest.title) },
            text = { Text("هذه الميزة قيد التطوير") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { futureNotice.value = null }) { Text("حسناً") } },
        )
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DestinationRow(destination: SettingsDestination, onClick: () -> Unit) {
    val container = if (destination.implemented) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val titleColor = if (destination.implemented) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = destination.implemented, onClick = onClick),
        shape = FinancialShapes.medium,
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = iconFor(destination), contentDescription = null, tint = titleColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(destination.title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                if (!destination.implemented) {
                    Text("قريباً", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (destination.implemented) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun iconFor(destination: SettingsDestination): ImageVector = when (destination) {
    SettingsDestinations.categoryManagement -> Icons.Filled.Style
    SettingsDestinations.merchantMemory -> Icons.Filled.Storefront
    SettingsDestinations.aiCategorization -> Icons.Filled.Settings
    SettingsDestinations.aiSuggestions -> Icons.Filled.Inbox
    SettingsDestinations.aiBatch -> Icons.Filled.Storefront
    SettingsDestinations.accounts -> Icons.Filled.AccountBox
    SettingsDestinations.linkTransactions -> Icons.Filled.AccountBox
    SettingsDestinations.financialHistory -> Icons.Filled.History
    SettingsDestinations.accountLinkRules -> Icons.Filled.AccountBox
    SettingsDestinations.senderMappings -> Icons.Filled.Info
    SettingsDestinations.diagnostics -> Icons.Filled.Info
    SettingsDestinations.testData -> Icons.Filled.Inbox
    SettingsDestinations.releaseNotes -> Icons.Filled.Info
    else -> Icons.Filled.Info
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinancialShapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
