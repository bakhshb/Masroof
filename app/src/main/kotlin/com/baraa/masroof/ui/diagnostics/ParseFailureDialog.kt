package com.baraa.masroof.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.diagnostics.ClipboardHelper
import com.baraa.masroof.diagnostics.TextSanitizer

/**
 * Dialog shown when the parser cannot turn an SMS into a transaction.
 * Lets the user:
 *  - تجاهل — dismiss and move on
 *  - إعادة المحاولة — caller re-parses the same body
 *  - نسخ نموذج منقح — opens a preview of the sanitized body, then
 *    copies to clipboard on confirm
 */
@Composable
fun ParseFailureDialog(
    rawBody: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var previewing by remember { mutableStateOf(false) }
    if (previewing) {
        SanitizedPreviewDialog(
            rawBody = rawBody,
            onDismiss = { previewing = false },
            onConfirm = { sanitized ->
                ClipboardHelper.copy(context, sanitized)
                onDismiss()
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parse_failure_title)) },
        text = {
            Text(
                text = stringResource(R.string.parse_failure_title),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.parse_failure_ignore))
                }
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.parse_failure_retry))
                }
                Button(onClick = { previewing = true }) {
                    Text(stringResource(R.string.parse_failure_copy_sanitized))
                }
            }
        },
    )
}

@Composable
private fun SanitizedPreviewDialog(
    rawBody: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sanitized = remember(rawBody) { TextSanitizer.sanitize(rawBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parse_failure_preview_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = sanitized,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sanitized) }) {
                Text(stringResource(R.string.parse_failure_preview_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.parse_failure_preview_cancel))
            }
        },
    )
}