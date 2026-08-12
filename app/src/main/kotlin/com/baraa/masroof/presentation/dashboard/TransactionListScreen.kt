package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    periodLabel: String,
    transactions: List<TransactionPreviewUi>,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCardLast4 by rememberSaveable { mutableStateOf<String?>(null) }

    val filterState = remember(searchQuery, selectedTypeName, selectedCardLast4) {
        TransactionListFilterState(
            searchQuery = searchQuery,
            type = selectedTypeName?.let { runCatching { FinancialTransactionType.valueOf(it) }.getOrNull() },
            cardLast4 = selectedCardLast4,
        )
    }

    val filterResult = remember(transactions, filterState) {
        TransactionListFilterEngine.apply(transactions, filterState)
    }
    val availableTypes = remember(transactions) {
        TransactionListFilterEngine.availableTypes(transactions)
    }
    val availableCards = remember(transactions) {
        TransactionListFilterEngine.availableCardLast4s(transactions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transaction_list_title)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.review_back),
                    )
                },
            )
        },
    ) { padding ->
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.dashboard_empty_period))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.transaction_list_period, periodLabel),
                    icon = MasroofIcons.calendar,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.transaction_list_search_hint)) },
                )
            }

            if (availableTypes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterState.type == null,
                            onClick = { selectedTypeName = null },
                            label = { Text(stringResource(R.string.transaction_list_filter_all_types)) },
                        )
                        availableTypes.forEach { type ->
                            FilterChip(
                                selected = filterState.type == type,
                                onClick = {
                                    selectedTypeName = if (filterState.type == type) null else type.name
                                },
                                label = { Text(transactionTypeLabel(type)) },
                            )
                        }
                    }
                }
            }

            if (availableCards.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filterState.cardLast4 == null,
                            onClick = { selectedCardLast4 = null },
                            label = { Text(stringResource(R.string.transaction_list_filter_all_cards)) },
                        )
                        availableCards.forEach { last4 ->
                            FilterChip(
                                selected = filterState.cardLast4 == last4,
                                onClick = {
                                    selectedCardLast4 = if (filterState.cardLast4 == last4) null else last4
                                },
                                label = {
                                    Text(stringResource(R.string.dashboard_credit_card_last4, last4))
                                },
                            )
                        }
                    }
                }
            }

            item {
                TransactionListSummaryLine(
                    filteredCount = filterResult.transactions.size,
                    totalCount = transactions.size,
                    totalAmount = filterResult.totalAmount,
                    filterActive = filterState.isActive,
                    onClearFilters = {
                        searchQuery = ""
                        selectedTypeName = null
                        selectedCardLast4 = null
                    },
                )
                Spacer(Modifier.height(4.dp))
            }

            if (filterResult.transactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.transaction_list_no_filter_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(filterResult.transactions, key = { it.id }) { row ->
                    TransactionRow(row, onClick = { onOpenTransaction(row.id) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransactionListSummaryLine(
    filteredCount: Int,
    totalCount: Int,
    totalAmount: com.baraa.masroof.core.money.Money?,
    filterActive: Boolean,
    onClearFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                if (filterActive) {
                    stringResource(
                        R.string.transaction_list_filtered_count,
                        filteredCount,
                        totalCount,
                    )
                } else {
                    stringResource(R.string.transaction_list_count, totalCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filterActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.transaction_list_clear_filters))
                }
            }
        }
        if (totalAmount != null && filteredCount > 0) {
            Text(
                stringResource(
                    R.string.transaction_list_filtered_total,
                    MoneyUiFormatter.format(totalAmount),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
