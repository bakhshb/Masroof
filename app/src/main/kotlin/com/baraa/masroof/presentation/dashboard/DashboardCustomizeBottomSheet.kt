package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.application.dashboard.DashboardLayoutSnapshot
import com.baraa.masroof.application.dashboard.DashboardSectionId
import com.baraa.masroof.application.dashboard.DashboardSectionSize
import com.baraa.masroof.presentation.common.MasroofIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizeBottomSheet(
    draft: DashboardLayoutSnapshot,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onToggleSection: (DashboardSectionId) -> Unit,
    onSetSectionSize: (DashboardSectionId, DashboardSectionSize) -> Unit,
    onMoveSection: (DashboardSectionId, Int) -> Unit,
    onToggleQuickExpense: () -> Unit,
    onToggleQuickIncome: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.dashboard_customize_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = draft.sections,
                    key = { it.id.name },
                ) { entry ->
                    CustomizeSectionRow(
                        entry = entry,
                        canMoveUp = draft.sections.indexOf(entry) > 0,
                        canMoveDown = draft.sections.indexOf(entry) < draft.sections.lastIndex,
                        onToggle = { onToggleSection(entry.id) },
                        onSizeSelected = { onSetSectionSize(entry.id, it) },
                        onMoveUp = { onMoveSection(entry.id, -1) },
                        onMoveDown = { onMoveSection(entry.id, 1) },
                        extraContent = if (entry.id == DashboardSectionId.QUICK) {
                            {
                                QuickChildToggle(
                                    label = stringResource(R.string.dashboard_quick_total_expense),
                                    checked = draft.quickExpenseVisible,
                                    onToggle = onToggleQuickExpense,
                                )
                                QuickChildToggle(
                                    label = stringResource(R.string.dashboard_quick_total_income),
                                    checked = draft.quickIncomeVisible,
                                    onToggle = onToggleQuickIncome,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.dashboard_customize_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSectionRow(
    entry: com.baraa.masroof.application.dashboard.DashboardSectionEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onSizeSelected: (DashboardSectionSize) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    extraContent: (@Composable () -> Unit)?,
) {
    var sizeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = sectionIcon(entry.id),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sectionTitle(entry.id),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                ExposedDropdownMenuBox(
                    expanded = sizeMenuExpanded,
                    onExpandedChange = { sizeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = sectionSizeLabel(entry.size),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        textStyle = MaterialTheme.typography.labelSmall,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sizeMenuExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = sizeMenuExpanded,
                        onDismissRequest = { sizeMenuExpanded = false },
                    ) {
                        DashboardSectionSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(sectionSizeLabel(size)) },
                                onClick = {
                                    onSizeSelected(size)
                                    sizeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            Switch(checked = entry.visible, onCheckedChange = { onToggle() })
        }
        extraContent?.invoke()
    }
}

@Composable
private fun QuickChildToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun sectionTitle(id: DashboardSectionId): String =
    stringResource(
        when (id) {
            DashboardSectionId.HERO -> R.string.dashboard_customize_section_hero
            DashboardSectionId.QUICK -> R.string.dashboard_customize_section_quick
            DashboardSectionId.ACCOUNTS -> R.string.dashboard_customize_section_accounts
            DashboardSectionId.CARDS -> R.string.dashboard_customize_section_cards
            DashboardSectionId.TRANSACTIONS -> R.string.dashboard_customize_section_transactions
        },
    )

@Composable
private fun sectionSizeLabel(size: DashboardSectionSize): String =
    stringResource(
        when (size) {
            DashboardSectionSize.SMALL -> R.string.dashboard_customize_size_small
            DashboardSectionSize.MEDIUM -> R.string.dashboard_customize_size_medium
            DashboardSectionSize.LARGE -> R.string.dashboard_customize_size_large
        },
    )

private fun sectionIcon(id: DashboardSectionId) =
    when (id) {
        DashboardSectionId.HERO -> MasroofIcons.appLogo
        DashboardSectionId.QUICK -> MasroofIcons.netCashFlow
        DashboardSectionId.ACCOUNTS -> MasroofIcons.moneyMovement
        DashboardSectionId.CARDS -> MasroofIcons.cardPayment
        DashboardSectionId.TRANSACTIONS -> MasroofIcons.recentTransactions
    }
