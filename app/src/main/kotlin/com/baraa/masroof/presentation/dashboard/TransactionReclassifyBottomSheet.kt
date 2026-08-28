package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.presentation.theme.MasroofIconSizes
import com.baraa.masroof.presentation.theme.MasroofSpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.transaction.TransactionReclassificationService
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.MasroofIcons

private val RECLASSIFY_TYPE_OPTIONS: List<FinancialTransactionType> =
    TransactionReclassificationService.ALLOWED_TYPES.toList().sortedBy { it.name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionReclassifyBottomSheet(
    currentType: FinancialTransactionType,
    selectedType: FinancialTransactionType?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSelectType: (FinancialTransactionType) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = selectedType != null && selectedType != currentType && !saving

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MasroofSpacing.bottomSheetBottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MasroofSpacing.sectionHeaderGap,
                        vertical = MasroofSpacing.inlineGap,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.transaction_reclassify_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = MasroofSpacing.sectionHeaderGap),
                )
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.settings_cancel),
                    )
                }
            }

            selectedType?.takeIf { it != currentType }?.let { pending ->
                Text(
                    stringResource(
                        R.string.transaction_reclassify_preview,
                        transactionTypeLabel(currentType),
                        transactionTypeLabel(pending),
                    ),
                    modifier = Modifier.padding(
                        horizontal = MasroofSpacing.screenHorizontal,
                        vertical = MasroofSpacing.sectionHeaderGap,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                RECLASSIFY_TYPE_OPTIONS.forEach { type ->
                    val isCurrent = type == currentType
                    val isSelected = type == selectedType
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent && !saving) {
                                onSelectType(type)
                            }
                            .padding(
                        horizontal = MasroofSpacing.sectionHeaderGap,
                        vertical = MasroofSpacing.inlineGap,
                    ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected || isCurrent,
                            onClick = {
                                if (!isCurrent) onSelectType(type)
                            },
                            enabled = !isCurrent && !saving,
                        )
                        Icon(
                            imageVector = MasroofIcons.transactionType(type),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(MasroofIconSizes.lg),
                        )
                        Spacer(Modifier.width(MasroofSpacing.carouselGap))
                        Text(
                            transactionTypeLabel(type),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Text(
                                stringResource(R.string.transaction_reclassify_current_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = MasroofSpacing.sectionHeaderGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MasroofSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(MasroofSpacing.sectionGap),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.transaction_reclassify_sheet_cancel))
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.transaction_reclassify_sheet_save))
                }
            }
        }
    }
}
