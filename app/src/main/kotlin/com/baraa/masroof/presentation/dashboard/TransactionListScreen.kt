package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.BackNavigationIcon

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
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

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
    val activeFilterCount = filterState.activeFilterCount()

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
                Text(
                    stringResource(R.string.dashboard_empty_period),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                TransactionListToolbar(
                    periodLabel = periodLabel,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filtersExpanded = filtersExpanded,
                    onToggleFiltersExpanded = { filtersExpanded = !filtersExpanded },
                    activeFilterCount = activeFilterCount,
                    filterState = filterState,
                    availableTypes = availableTypes,
                    availableCards = availableCards,
                    onTypeSelected = { type ->
                        selectedTypeName = type?.name
                    },
                    onCardSelected = { card ->
                        selectedCardLast4 = card
                    },
                    onClearFilters = {
                        searchQuery = ""
                        selectedTypeName = null
                        selectedCardLast4 = null
                        filtersExpanded = false
                    },
                    filteredCount = filterResult.transactions.size,
                    totalCount = transactions.size,
                    totalAmount = filterResult.totalAmount,
                    filterActive = filterState.isActive,
                )
                Spacer(Modifier.height(4.dp))
            }

            if (filterResult.transactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.transaction_list_no_filter_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                    )
                }
            } else {
                items(filterResult.transactions, key = { it.id }) { row ->
                    TransactionListRow(
                        row = row,
                        onClick = { onOpenTransaction(row.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
