package com.baraa.masroof.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListToolbar(
    periodLabel: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filtersExpanded: Boolean,
    onToggleFiltersExpanded: () -> Unit,
    activeFilterCount: Int,
    filterState: TransactionListFilterState,
    availableTypes: List<FinancialTransactionType>,
    availableCards: List<String>,
    onTypeToggle: (FinancialTransactionType) -> Unit,
    onClearTypes: () -> Unit,
    onCardToggle: (String) -> Unit,
    onClearCards: () -> Unit,
    onClearFilters: () -> Unit,
    filteredCount: Int,
    totalCount: Int,
    totalAmount: Money?,
    filterActive: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.transaction_list_period, periodLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    stringResource(R.string.transaction_list_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.transaction_list_clear_search),
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text(activeFilterCount.toString()) }
                    }
                },
            ) {
                AssistChip(
                    onClick = onToggleFiltersExpanded,
                    label = { Text(stringResource(R.string.transaction_list_filters_toggle)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (filtersExpanded || activeFilterCount > 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                )
            }

            if (filterActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.transaction_list_clear_filters))
                }
            }
        }

        AnimatedVisibility(
            visible = filtersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TransactionListFilterSheet(
                filterState = filterState,
                availableTypes = availableTypes,
                availableCards = availableCards,
                onTypeToggle = onTypeToggle,
                onClearTypes = onClearTypes,
                onCardToggle = onCardToggle,
                onClearCards = onClearCards,
            )
        }

        TransactionListResultsBanner(
            filteredCount = filteredCount,
            totalCount = totalCount,
            totalAmount = totalAmount,
            filterActive = filterActive,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionListFilterSheet(
    filterState: TransactionListFilterState,
    availableTypes: List<FinancialTransactionType>,
    availableCards: List<String>,
    onTypeToggle: (FinancialTransactionType) -> Unit,
    onClearTypes: () -> Unit,
    onCardToggle: (String) -> Unit,
    onClearCards: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.transaction_list_multi_filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (availableTypes.isNotEmpty()) {
                FilterChipSection(
                    title = stringResource(R.string.transaction_list_filter_type_section),
                ) {
                    FilterChip(
                        selected = filterState.types.isEmpty(),
                        onClick = onClearTypes,
                        label = { Text(stringResource(R.string.transaction_list_filter_all_types)) },
                        colors = filterChipColors(),
                    )
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = type in filterState.types,
                            onClick = { onTypeToggle(type) },
                            label = { Text(transactionTypeLabel(type)) },
                            colors = filterChipColors(),
                        )
                    }
                }
            }

            if (availableCards.isNotEmpty()) {
                FilterChipSection(
                    title = stringResource(R.string.transaction_list_filter_card_section),
                ) {
                    FilterChip(
                        selected = filterState.cardLast4s.isEmpty(),
                        onClick = onClearCards,
                        label = { Text(stringResource(R.string.transaction_list_filter_all_cards)) },
                        colors = filterChipColors(),
                    )
                    availableCards.forEach { last4 ->
                        FilterChip(
                            selected = last4 in filterState.cardLast4s,
                            onClick = { onCardToggle(last4) },
                            label = {
                                Text(stringResource(R.string.dashboard_credit_card_last4, last4))
                            },
                            colors = filterChipColors(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipSection(
    title: String,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

@Composable
private fun TransactionListResultsBanner(
    filteredCount: Int,
    totalCount: Int,
    totalAmount: Money?,
    filterActive: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (filterActive) {
                    stringResource(
                        R.string.transaction_list_filtered_count,
                        filteredCount,
                        totalCount,
                    )
                } else {
                    stringResource(R.string.transaction_list_count, totalCount)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (totalAmount != null && filteredCount > 0) {
                Text(
                    text = stringResource(
                        R.string.transaction_list_filtered_total,
                        MoneyUiFormatter.format(totalAmount),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

fun TransactionListFilterState.activeFilterCount(): Int {
    var count = types.size + cardLast4s.size
    if (searchQuery.isNotBlank()) count++
    return count
}
