package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(
    appVersion: String,
    githubTokenConfigured: Boolean,
    updateState: AppUpdateUiState,
    updateMessage: AppUpdateMessage?,
    onBack: () -> Unit,
    onSaveGithubToken: (String) -> Unit,
    onClearGithubToken: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onClearUpdateMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var tokenInput by rememberSaveable { mutableStateOf("") }
    val updateMessageText = updateMessage?.let { resolveUpdateMessage(it) }

    LaunchedEffect(updateMessageText) {
        if (updateMessageText != null) {
            snackbarHostState.showSnackbar(updateMessageText)
            onClearUpdateMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MasroofSecondaryScaffold(
            title = stringResource(R.string.settings_about_section),
            onBack = onBack,
            backContentDescription = stringResource(R.string.settings_back),
        ) { contentModifier ->
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Icon(
                imageVector = MasroofIcons.appLogo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.settings_about_version, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            AboutInfoCard(
                icon = MasroofIcons.periodHint,
                title = stringResource(R.string.settings_about_privacy_title),
                body = stringResource(R.string.settings_about_privacy_body),
            )
            AboutInfoCard(
                icon = MasroofIcons.externalIn,
                title = stringResource(R.string.settings_about_banks_title),
                body = stringResource(R.string.bank_aljazira),
            )

            UpdateSectionCard(
                githubTokenConfigured = githubTokenConfigured,
                tokenInput = tokenInput,
                onTokenInputChange = { tokenInput = it },
                onSaveGithubToken = {
                    onSaveGithubToken(tokenInput)
                    tokenInput = ""
                },
                onClearGithubToken = {
                    tokenInput = ""
                    onClearGithubToken()
                },
                updateState = updateState,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
            )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun UpdateSectionCard(
    githubTokenConfigured: Boolean,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    onSaveGithubToken: () -> Unit,
    onClearGithubToken: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    MasroofCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconLabelRow(
                icon = MasroofIcons.export,
                label = stringResource(R.string.settings_updates_title),
                iconTint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.settings_updates_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_github_token_label)) },
                placeholder = { Text(stringResource(R.string.settings_github_token_placeholder)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )

            RowActions(
                primaryLabel = stringResource(R.string.settings_github_token_save),
                onPrimary = onSaveGithubToken,
                primaryEnabled = tokenInput.isNotBlank(),
                secondaryLabel = if (githubTokenConfigured) {
                    stringResource(R.string.settings_github_token_clear)
                } else {
                    null
                },
                onSecondary = if (githubTokenConfigured) onClearGithubToken else null,
            )

            Text(
                if (githubTokenConfigured) {
                    stringResource(R.string.settings_github_token_configured)
                } else {
                    stringResource(R.string.settings_github_token_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onCheckForUpdates,
                enabled = updateState !is AppUpdateUiState.Checking &&
                    updateState !is AppUpdateUiState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (updateState is AppUpdateUiState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.settings_check_updates))
                }
            }

            when (updateState) {
                AppUpdateUiState.Idle -> Unit
                AppUpdateUiState.Checking -> Unit
                AppUpdateUiState.UpToDate ->
                    Text(
                        stringResource(R.string.settings_updates_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                is AppUpdateUiState.Available ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                R.string.settings_updates_available,
                                updateState.manifest.versionName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        updateState.manifest.releaseNotes?.let { notes ->
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onDownloadUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_download_update))
                        }
                    }

                is AppUpdateUiState.Downloading -> {
                    val progress =
                        if (updateState.totalBytes > 0L) {
                            updateState.bytesRead.toFloat() / updateState.totalBytes.toFloat()
                        } else {
                            0f
                        }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(progress = { progress })
                        Text(
                            stringResource(R.string.settings_downloading_update),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is AppUpdateUiState.ReadyToInstall ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                R.string.settings_updates_ready,
                                updateState.manifest.versionName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Button(
                            onClick = onInstallUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_install_update))
                        }
                    }
            }
        }
    }
}

@Composable
private fun RowActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
private fun resolveUpdateMessage(message: AppUpdateMessage): String =
    when (message) {
        AppUpdateMessage.UP_TO_DATE -> stringResource(R.string.settings_updates_up_to_date)
        AppUpdateMessage.UPDATE_AVAILABLE -> stringResource(R.string.settings_updates_available_snackbar)
        AppUpdateMessage.DOWNLOAD_SUCCESS -> stringResource(R.string.settings_download_success)
        AppUpdateMessage.TOKEN_SAVED -> stringResource(R.string.settings_github_token_saved)
        AppUpdateMessage.TOKEN_REQUIRED -> stringResource(R.string.settings_github_token_required)
        AppUpdateMessage.AUTH_FAILED -> stringResource(R.string.settings_github_auth_failed)
        AppUpdateMessage.CHECK_FAILED -> stringResource(R.string.settings_updates_check_failed)
        AppUpdateMessage.DOWNLOAD_FAILED -> stringResource(R.string.settings_download_failed)
        AppUpdateMessage.INSTALL_FAILED -> stringResource(R.string.settings_install_failed)
        AppUpdateMessage.INSTALL_PERMISSION_REQUIRED ->
            stringResource(R.string.settings_install_permission_required)
    }

@Composable
private fun AboutInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    MasroofCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconLabelRow(icon = icon, label = title, iconTint = MaterialTheme.colorScheme.primary)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
