package com.baraa.masroof.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.repository.ImportPreview
import com.baraa.masroof.data.repository.ImportSummary

/**
 * Pre-import confirmation dialog. Shows the 5 counts (scanned, parsed,
 * unparseable, new, duplicates) and asks the user to confirm.
 */
@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.import_preview_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(id = R.string.import_preview_scanned, preview.messagesScanned))
                Text(stringResource(id = R.string.import_preview_parsed, preview.parsedSuccessfully))
                Text(stringResource(id = R.string.import_preview_unparseable, preview.unparseable))
                Text(stringResource(id = R.string.import_preview_new, preview.newTransactions))
                Text(stringResource(id = R.string.import_preview_duplicates, preview.duplicatesSkipped))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = preview.hasAnythingToImport,
            ) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}

/**
 * Post-import summary dialog. Shows what was inserted vs skipped vs unparseable.
 */
@Composable
fun ImportSummaryDialog(
    summary: ImportSummary,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.import_summary_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(id = R.string.import_summary_inserted, summary.inserted))
                Text(stringResource(id = R.string.import_summary_duplicates, summary.duplicatesSkipped))
                Text(stringResource(id = R.string.import_summary_unparseable, summary.unparseable))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_continue))
            }
        },
    )
}
