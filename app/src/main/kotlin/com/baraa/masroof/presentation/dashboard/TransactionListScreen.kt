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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.ShareActionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    periodLabel: String,
    transactions: List<TransactionPreviewUi>,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTypeNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var selectedCardLast4s by rememberSaveable { mutableStateOf(listOf<String>()) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val filterState = remember(searchQuery, selectedTypeNames, selectedCardLast4s) {
        TransactionListFilterState(
            searchQuery = searchQuery,
            types = selectedTypeNames.mapNotNull { name ->
                runCatching { FinancialTransactionType.valueOf(name) }.getOrNull()
            }.toSet(),
            cardLast4s = selectedCardLast4s.toSet(),
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
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.transaction_share)
    val shareSubject = stringResource(R.string.transaction_list_title)
    val shareText = transactionListShareText(
        periodLabel = periodLabel,
        filter = filterState,
        transactions = filterResult.transactions,
        totalAmount = filterResult.totalAmount,
    )
    val canShare = filterResult.transactions.isNotEmpty()

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
                actions = {
                    ShareActionIcon(
                        enabled = canShare,
                        onClick = {
                            SharePlainText.share(
                                context = context,
                                text = shareText,
                                chooserTitle = shareChooserTitle,
                                subject = shareSubject,
                            )
                        },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
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
                onTypeToggle = { type ->
                    selectedTypeNames = if (type.name in selectedTypeNames) {
                        selectedTypeNames - type.name
                    } else {
                        selectedTypeNames + type.name
                    }
                },
                onClearTypes = { selectedTypeNames = emptyList() },
                onCardToggle = { last4 ->
                    selectedCardLast4s = if (last4 in selectedCardLast4s) {
                        selectedCardLast4s - last4
                    } else {
                        selectedCardLast4s + last4
                    }
                },
                onClearCards = { selectedCardLast4s = emptyList() },
                onClearFilters = {
                    searchQuery = ""
                    selectedTypeNames = emptyList()
                    selectedCardLast4s = emptyList()
                    filtersExpanded = false
                },
                filteredCount = filterResult.transactions.size,
                totalCount = transactions.size,
                totalAmount = filterResult.totalAmount,
                filterActive = filterState.isActive,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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
}
