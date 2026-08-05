package com.baraa.masroof.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.R
import com.baraa.masroof.diagnostics.DiagnosticCollector
import com.baraa.masroof.diagnostics.DiagnosticReport
import com.baraa.masroof.diagnostics.DiagnosticShareHelper
import com.baraa.masroof.diagnostics.DiagnosticSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * تشخيص التطبيق — diagnostics screen.
 *
 * The screen pulls a fresh [DiagnosticSnapshot] from the
 * [DiagnosticCollector] every time it's shown, and offers a single
 * "تصدير تقرير التشخيص" action that writes a sanitized report to the
 * app cache and surfaces an Android Sharesheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<DiagnosticSnapshot?>(null) }

    LaunchedEffect(Unit) {
        snapshot = withContext(Dispatchers.IO) { app.diagnosticCollector.snapshot() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.diagnostics_title)) },
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
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val s = snapshot
            if (s == null) {
                Text(text = "…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_version_name),
                value = "${s.appVersionName} (${s.appVersionCode})",
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_db_version),
                value = s.databaseSchemaVersion.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_sms_permission),
                value = if (s.smsPermissionGranted) stringResource(R.string.diagnostics_perm_granted)
                else stringResource(R.string.diagnostics_perm_denied),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_sms_scanned),
                value = s.smsScannedCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_sms_financial),
                value = s.smsFinancialDetectedCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_sms_parsed),
                value = s.smsParsedCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_sms_parse_failure),
                value = s.smsParseFailureCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_saved_tx),
                value = s.savedTransactionsCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_exact_dups),
                value = s.exactDuplicatesCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_possible_dups),
                value = s.possibleDuplicatesCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_needs_review),
                value = s.needsReviewCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_categories),
                value = s.categoryCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_merchants),
                value = s.merchantMemoryCount.toString(),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_ai_status),
                value = if (s.aiEnabled) stringResource(R.string.diagnostics_ai_enabled)
                else stringResource(R.string.diagnostics_ai_disabled),
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_ai_provider),
                value = s.aiProviderName ?: "—",
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_ai_model),
                value = s.aiModelName ?: "—",
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_last_ai_outcome),
                value = s.lastAiOutcome,
            )
            DiagnosticsCard(
                title = stringResource(R.string.diagnostics_last_error),
                value = s.recentErrors.lastOrNull()?.let {
                    "${it.category}: ${it.message}"
                } ?: stringResource(R.string.diagnostics_no_errors),
            )
            // --- Receiver diagnostics (section L) ---
            Text("استقبال الرسائل", style = MaterialTheme.typography.titleMedium)
            DiagnosticsCard(title = "READ_SMS", value = if (s.smsPermissionGranted) "ممنوح" else "غير ممنوح")
            val receiveSms = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
            DiagnosticsCard(title = "RECEIVE_SMS", value = if (receiveSms) "ممنوح" else "غير ممنوح")
            val postNotifs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            } else true
            DiagnosticsCard(title = "POST_NOTIFICATIONS", value = if (postNotifs) "ممنوح" else "غير ممنوح")
            DiagnosticsCard(title = "استيراد الرسائل تلقائياً", value = if (app.developerPreferences.automaticSmsImportEnabled) "مفعّل" else "متوقف")
            DiagnosticsCard(title = "إشعارات المعاملات", value = if (app.developerPreferences.transactionNotificationsEnabled) "مفعّلة" else "متوقفة")
            DiagnosticsCard(title = "آخر تشغيل للمستقبل", value = if (app.developerPreferences.lastReceiverTriggerAt == 0L) "—" else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(app.developerPreferences.lastReceiverTriggerAt)))
            DiagnosticsCard(title = "آخر مرسل", value = app.developerPreferences.lastReceiverSender ?: "—")
            DiagnosticsCard(title = "آخر نتيجة استيراد تلقائي", value = app.developerPreferences.lastReceiverResult ?: "—")
            DiagnosticsCard(title = "آخر نتيجة إشعار", value = app.developerPreferences.lastNotificationResult ?: "—")
            DiagnosticsCard(title = "عدد العمليات المستوردة تلقائياً", value = app.developerPreferences.autoImportedCount.toString())
            DiagnosticsCard(title = "عدد العمليات التي تحتاج مراجعة", value = app.developerPreferences.autoNeedsReviewCount.toString())
            DiagnosticsCard(title = "عدد المكررات", value = app.developerPreferences.autoDuplicateCount.toString())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val s2 = withContext(Dispatchers.IO) { app.diagnosticCollector.snapshot() }
                            DiagnosticShareHelper.share(
                                context = context,
                                snapshot = s2,
                                app = app,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.diagnostics_export)) }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val sample = com.baraa.masroof.diagnostics.FakeSmsSamples.samples.firstOrNull()
                                if (sample != null) {
                                    val synth = com.baraa.masroof.sms.SmsMessage(
                                        id = -System.currentTimeMillis(),
                                        sender = sample.sender,
                                        body = sample.body,
                                        timestamp = System.currentTimeMillis(),
                                    )
                                    val tracking = app.financialSetupRepository.load().let {
                                        java.time.Instant.ofEpochMilli(it.trackingStartDate)
                                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    }
                                    val result = app.importOrchestrator.processIncoming(listOf(synth), tracking)
                                    app.developerPreferences.lastReceiverTriggerAt = System.currentTimeMillis()
                                    app.developerPreferences.lastReceiverSender = sample.sender
                                    app.developerPreferences.lastReceiverResult = "imported=${result.importedTransactions} linked=${result.linkedTransactions} review=${result.needsReviewTransactions}"
                                    app.developerPreferences.autoImportedCount += result.importedTransactions
                                    app.developerPreferences.autoNeedsReviewCount += result.needsReviewTransactions
                                    app.developerPreferences.autoDuplicateCount += result.duplicateTransactions
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("اختبار استقبال رسالة جديدة") }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
