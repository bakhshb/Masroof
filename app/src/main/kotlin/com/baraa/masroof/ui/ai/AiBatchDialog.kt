package com.baraa.masroof.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.baraa.masroof.ai.AiBatchCategorizationService
import com.baraa.masroof.ai.BatchPlan
import com.baraa.masroof.ai.BatchState
import kotlinx.coroutines.launch

/**
 * Arabic batch flow for "تصنيف العمليات غير المصنفة".
 *
 * Usage:
 *  - The caller shows this dialog when AI is enabled and at least one
 *    eligible transaction exists.
 *  - When AI is disabled, the caller should show [AiBatchDisabledDialog]
 *    instead with a "open settings" route.
 */
@Composable
fun AiBatchDialog(
    plan: BatchPlan,
    providerLabel: String,
    modelName: String,
    onDismiss: () -> Unit,
    batchService: AiBatchCategorizationService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by batchService.state.collectAsStateCompat()
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        // Once the batch reaches Done or Error, the dialog can close.
        if (started && (state is BatchState.Done || state is BatchState.Error)) {
            // Caller can dismiss from the underlying screen.
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (state is BatchState.Running) batchService.cancel()
            onDismiss()
        },
        title = { Text(stringResource(R.string.ai_batch_dialog_title)) },
        text = {
            Column {
                when (val s = state) {
                    is BatchState.Idle -> {
                        Text(
                            text = stringResource(
                                R.string.ai_batch_dialog_summary,
                                plan.eligible, plan.cached, plan.remote, providerLabel, modelName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.ai_batch_dialog_privacy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    is BatchState.Running -> {
                        val progress = if (s.total == 0) 0f else s.processed.toFloat() / s.total
                        Text(
                            text = stringResource(
                                R.string.ai_batch_progress_label, s.processed, s.total,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.ai_batch_progress_cache_hits, s.cacheHits),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.ai_batch_progress_succeeded, s.succeeded),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.ai_batch_progress_failed, s.failed),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.ai_batch_progress_skipped, s.skipped),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (s.canceled) {
                            Text(
                                text = stringResource(R.string.ai_batch_progress_canceled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is BatchState.Done -> {
                        val sum = s.summary
                        Text(
                            text = stringResource(R.string.ai_batch_progress_done),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${sum.succeeded} / ${sum.processed}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is BatchState.Error -> {
                        Text(text = s.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is BatchState.Idle -> {
                    Button(onClick = {
                        started = true
                        scope.launch { batchService.start() }
                    }) {
                        Text(stringResource(R.string.ai_batch_dialog_start))
                    }
                }
                is BatchState.Running -> {
                    Button(onClick = { batchService.cancel() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                else -> {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.action_continue))
                    }
                }
            }
        },
        dismissButton = {
            if (state is BatchState.Running) {
                OutlinedButton(onClick = { batchService.cancel() }) {
                    Text(stringResource(R.string.ai_batch_progress_canceled))
                }
            } else {
                TextButton(onClick = {
                    if (state is BatchState.Running) batchService.cancel()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.ai_batch_dialog_cancel))
                }
            }
        },
    )
}

/**
 * Shown when AI is disabled. Offers a "open settings" route instead of
 * silently failing.
 */
@Composable
fun AiBatchDisabledDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_batch_disabled_title)) },
        text = { Text(stringResource(R.string.ai_batch_disabled_body)) },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.ai_batch_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Convenience helper: convert a [kotlinx.coroutines.flow.StateFlow] into a
 * Compose [androidx.compose.runtime.State] with `collectAsState`. Kept
 * here so we don't pull another import into this file.
 */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    this.collectAsState()

/**
 * Card shown on the transactions list when AI is enabled and at least
 * one transaction exists. Tap to open the batch dialog.
 */
@Composable
fun AiBatchActionCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_batch_action),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "›",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
        }
    }
}