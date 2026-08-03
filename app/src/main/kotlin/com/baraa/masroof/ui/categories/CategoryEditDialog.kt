package com.baraa.masroof.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.data.db.Category

/**
 * Add / edit dialog for a single category. Includes a "Delete" button when
 * editing an existing non-system category.
 */
@Composable
fun CategoryEditDialog(
    existing: Category?,
    parentId: Long?,
    parentName: String?,
    onDismiss: () -> Unit,
    onSave: (nameAr: String, nameEn: String?, sortOrder: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var nameAr by remember { mutableStateOf(existing?.nameAr.orEmpty()) }
    var nameEn by remember { mutableStateOf(existing?.nameEn.orEmpty()) }
    var sortOrder by remember { mutableStateOf((existing?.sortOrder ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (existing == null) R.string.category_edit_title_add
                    else R.string.category_edit_title_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                parentName?.let {
                    Text(
                        text = stringResource(id = R.string.category_parent_label, it),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    label = { Text(stringResource(id = R.string.category_field_name_ar)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(stringResource(id = R.string.category_field_name_en)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sortOrder,
                    onValueChange = { sortOrder = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(id = R.string.category_field_sort_order)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(id = R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sort = sortOrder.toIntOrNull() ?: 0
                    onSave(nameAr.trim(), nameEn.trim().ifBlank { null }, sort)
                },
                enabled = nameAr.isNotBlank(),
            ) { Text(stringResource(id = R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.action_cancel))
            }
        },
    )
}
