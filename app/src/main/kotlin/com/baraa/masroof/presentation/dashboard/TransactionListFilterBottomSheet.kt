package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListFilterBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    filterState: TransactionListFilterState,
    availableTypes: List<FinancialTransactionType>,
    availableCards: List<String>,
    availableAccounts: List<String>,
    ownedCards: List<OwnedCardUi> = emptyList(),
    ownedAccounts: List<OwnedAccountUi> = emptyList(),
    onTypeToggle: (FinancialTransactionType) -> Unit,
    onClearTypes: () -> Unit,
    onCardToggle: (String) -> Unit,
    onClearCards: () -> Unit,
    onAccountToggle: (String) -> Unit,
    onClearAccounts: () -> Unit,
    onClearFilters: () -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = DashboardSpacing.bottomSheetBottom),
            verticalArrangement = Arrangement.spacedBy(DashboardSpacing.bottomSheetSectionGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.transaction_list_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (filterState.isActive) {
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.transaction_list_clear_filters))
                    }
                }
            }

            Text(
                text = stringResource(R.string.transaction_list_multi_filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (availableTypes.isNotEmpty()) {
                TransactionListFilterChipSection(
                    title = stringResource(R.string.transaction_list_filter_type_section),
                ) {
                    FilterChip(
                        selected = filterState.types.isEmpty(),
                        onClick = onClearTypes,
                        label = { Text(stringResource(R.string.transaction_list_filter_all_types)) },
                        colors = transactionListFilterChipColors(),
                    )
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = type in filterState.types,
                            onClick = { onTypeToggle(type) },
                            label = { Text(transactionTypeLabel(type)) },
                            colors = transactionListFilterChipColors(),
                        )
                    }
                }
            }

            if (availableAccounts.isNotEmpty()) {
                TransactionListFilterChipSection(
                    title = stringResource(R.string.transaction_list_filter_account_section),
                ) {
                    FilterChip(
                        selected = filterState.accountContainerIds.isEmpty(),
                        onClick = onClearAccounts,
                        label = { Text(stringResource(R.string.transaction_list_filter_all_accounts)) },
                        colors = transactionListFilterChipColors(),
                    )
                    availableAccounts.forEach { containerId ->
                        FilterChip(
                            selected = containerId in filterState.accountContainerIds,
                            onClick = { onAccountToggle(containerId) },
                            label = {
                                Text(
                                    accountDisplayLabel(ownedAccounts, containerId) ?: containerId,
                                )
                            },
                            colors = transactionListFilterChipColors(),
                        )
                    }
                }
            }

            if (availableCards.isNotEmpty()) {
                TransactionListFilterChipSection(
                    title = stringResource(R.string.transaction_list_filter_card_section),
                ) {
                    FilterChip(
                        selected = filterState.cardLast4s.isEmpty(),
                        onClick = onClearCards,
                        label = { Text(stringResource(R.string.transaction_list_filter_all_cards)) },
                        colors = transactionListFilterChipColors(),
                    )
                    availableCards.forEach { last4 ->
                        FilterChip(
                            selected = last4 in filterState.cardLast4s,
                            onClick = { onCardToggle(last4) },
                            label = {
                                Text(cardDisplayLabel(ownedCards, last4))
                            },
                            colors = transactionListFilterChipColors(),
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.transaction_list_filter_apply))
            }
        }
    }
}

@Composable
private fun TransactionListFilterChipSection(
    title: String,
    chips: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transactionListFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
