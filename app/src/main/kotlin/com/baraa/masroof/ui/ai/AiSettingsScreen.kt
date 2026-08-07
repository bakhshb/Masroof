package com.baraa.masroof.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.ai.AiDeploymentMode
import com.baraa.masroof.ai.AiProviderConfig
import kotlinx.coroutines.launch

/**
 * Settings for remote OpenAI-compatible **category** suggestions.
 * Account linking uses on-device message patterns — no model download UI here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var providerLabel by remember { mutableStateOf("OpenAI-compatible") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com") }
    var modelName by remember { mutableStateOf("gpt-4o-mini") }
    var apiKey by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(false) }
    var shareExact by remember { mutableStateOf(false) }
    var minConfidence by remember { mutableFloatStateOf(80f) }
    var requireHttps by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

    fun persist() {
        scope.launch {
            val cfg = AiProviderConfig(
                enabled = enabled,
                deploymentMode = AiDeploymentMode.REMOTE,
                providerLabel = providerLabel,
                baseUrl = baseUrl,
                modelName = modelName,
                onDeviceModelPath = "",
                apiKey = if (apiKey.isBlank() && hasKey) "<saved>" else apiKey,
                shareExactAmount = shareExact,
                minimumConfidence = minConfidence.toInt().coerceIn(0, 100),
                requireHttps = requireHttps,
            )
            if (apiKey.isNotBlank()) {
                app.aiSettingsRepository.saveApiKey(apiKey)
                apiKey = ""
                hasKey = true
                app.aiSettingsRepository.saveNonSecret(cfg.copy(apiKey = "<saved>"))
            } else {
                app.aiSettingsRepository.saveNonSecret(cfg.copy(apiKey = if (hasKey) "<saved>" else ""))
            }
            status = context.getString(R.string.ai_settings_status_enabled)
        }
    }

    LaunchedEffect(Unit) {
        val cfg = app.aiSettingsRepository.load()
        enabled = cfg.enabled
        providerLabel = if (cfg.deploymentMode == AiDeploymentMode.ON_DEVICE) {
            "OpenAI-compatible"
        } else {
            cfg.providerLabel
        }
        baseUrl = cfg.baseUrl
        modelName = cfg.modelName
        hasKey = cfg.apiKey.isNotBlank()
        shareExact = cfg.shareExactAmount
        minConfidence = cfg.minimumConfidence.toFloat()
        requireHttps = cfg.requireHttps
        loaded = true
        if (cfg.deploymentMode == AiDeploymentMode.ON_DEVICE) {
            app.aiSettingsRepository.saveNonSecret(
                cfg.copy(
                    deploymentMode = AiDeploymentMode.REMOTE,
                    providerLabel = "OpenAI-compatible",
                    onDeviceModelPath = "",
                    apiKey = if (cfg.apiKey.isNotBlank()) "<saved>" else "",
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(inner)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(id = R.string.ai_settings_privacy_notice),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(id = R.string.ai_settings_linking_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            SettingsSwitch(
                title = stringResource(id = R.string.ai_settings_enabled),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    persist()
                },
            )

            OutlinedTextField(
                value = providerLabel,
                onValueChange = { providerLabel = it; persist() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.ai_settings_provider)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.ai_settings_base_url)) },
                singleLine = true,
            )
            if (baseUrl.startsWith("http://")) {
                Text(
                    text = stringResource(id = R.string.ai_settings_http_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it; persist() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.ai_settings_model)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = if (hasKey && apiKey.isBlank()) {
                    stringResource(id = R.string.ai_settings_api_key_saved)
                } else {
                    apiKey
                },
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(id = R.string.ai_settings_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text(
                        text = if (hasKey) stringResource(id = R.string.ai_settings_api_key_saved) else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { persist() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.action_save))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            app.aiSettingsRepository.deleteApiKey()
                            hasKey = false
                            apiKey = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.ai_settings_api_key_clear))
                }
            }

            HorizontalDivider()

            SettingsSwitch(
                title = stringResource(id = R.string.ai_settings_share_exact_amount),
                checked = shareExact,
                onCheckedChange = { shareExact = it; persist() },
            )

            Text(
                text = stringResource(id = R.string.ai_settings_min_confidence),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = minConfidence,
                onValueChange = { minConfidence = it },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${minConfidence.toInt()}%", style = MaterialTheme.typography.bodySmall)

            SettingsSwitch(
                title = stringResource(id = R.string.ai_settings_require_https),
                checked = requireHttps,
                onCheckedChange = { requireHttps = it; persist() },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { scope.launch { testResult = runTest(app) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.ai_settings_test_connection))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            app.aiCacheRepository.clearAll()
                            status = context.getString(R.string.ai_settings_clear_cache_done)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(id = R.string.ai_settings_clear_cache))
                }
            }

            testResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            status?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private suspend fun runTest(app: MasroofApplication): String {
    val cfg = app.aiSettingsRepository.load()
    if (!cfg.enabled) {
        return app.getString(R.string.ai_settings_status_disabled)
    }
    return try {
        val service = app.aiCategorizationService()
        val outcome = service.categorize(
            merchant = "_test_",
            request = com.baraa.masroof.ai.AiCategorizationRequest(
                normalizedMerchant = "_test_",
                transactionType = "PURCHASE",
                amountBucket = com.baraa.masroof.ai.AmountBucket.UNDER_50,
                currency = com.baraa.masroof.transaction.Currency.SAR,
                allowedCategories = emptyList(),
                channel = com.baraa.masroof.ai.Channel.UNKNOWN,
                language = "ar",
            ),
        )
        when (outcome) {
            is com.baraa.masroof.ai.AiCategorizationOutcome.Success ->
                app.getString(R.string.ai_settings_test_success_ar)
            is com.baraa.masroof.ai.AiCategorizationOutcome.Failed ->
                app.getString(R.string.ai_settings_test_failed, outcome.reason.name)
            com.baraa.masroof.ai.AiCategorizationOutcome.Unclassified ->
                app.getString(R.string.ai_settings_test_success_ar)
        }
    } catch (t: Throwable) {
        app.getString(R.string.ai_settings_test_failed, t.message ?: t.javaClass.simpleName)
    }
}
