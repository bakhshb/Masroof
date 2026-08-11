package com.baraa.masroof.presentation.review

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.FinancialTransactionType

@Composable
fun ReviewRoute(
    viewModel: ReviewViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    if (state.selectedDetail != null) {
        ReviewDetailScreen(
            detail = state.selectedDetail!!,
            resolving = state.resolving,
            message = state.message,
            error = state.error,
            actionErrorDetail = state.actionErrorDetail,
            onBack = viewModel::closeDetail,
            onResolveType = viewModel::resolveAsFinancialType,
            onResolveExternal = viewModel::resolveAsExternalTransfer,
            onResolvePair = viewModel::resolveSelfTransferPair,
            onDismissNonFinancial = viewModel::resolveAsNonFinancial,
        )
    } else {
        ReviewListScreen(
            state = state,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onOpen = viewModel::openDetail,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewListScreen(
    state: ReviewUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.review_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error == ReviewError.LOAD_FAILED && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.review_load_error))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) {
                        Text(stringResource(R.string.dashboard_retry))
                    }
                }
            }
            state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.review_empty),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.review_back))
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            stringResource(R.string.review_count, state.items.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(state.items, key = { it.id }) { item ->
                        ReviewListCard(item = item, onClick = { onOpen(item.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ReviewListCard(item: ReviewListItemUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                item.amountLabel?.let { Text(it) }
            }
            Text(stringResource(item.kindLabelRes), style = MaterialTheme.typography.bodySmall)
            Text(item.dateLabel, style = MaterialTheme.typography.bodySmall)
            val reasonRes = ReviewReasonLabels.labelRes(item.reasonLabel)
            Text(
                reasonRes?.let { stringResource(it) } ?: item.reasonLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDetailScreen(
    detail: ReviewDetailUi,
    resolving: Boolean,
    message: ReviewMessage?,
    error: ReviewError?,
    actionErrorDetail: String?,
    onBack: () -> Unit,
    onResolveType: (FinancialTransactionType) -> Unit,
    onResolveExternal: () -> Unit,
    onResolvePair: (String) -> Unit,
    onDismissNonFinancial: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_detail_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.review_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(detail.kindLabelRes), style = MaterialTheme.typography.titleMedium)
            detail.amountLabel?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall)
            }
            detail.sender?.let {
                Text(stringResource(R.string.review_sender, it))
            }
            Text(stringResource(R.string.review_date, detail.dateLabel))
            detail.merchant?.let { Text(stringResource(R.string.review_merchant, it)) }
            detail.counterparty?.let { Text(stringResource(R.string.review_counterparty, it)) }

            Text(stringResource(R.string.review_reasons_title), style = MaterialTheme.typography.titleSmall)
            detail.reasonLabels.forEach { reason ->
                val reasonRes = ReviewReasonLabels.labelRes(reason)
                Text("• ${reasonRes?.let { stringResource(it) } ?: reason}")
            }

            Text(stringResource(R.string.review_sms_body), style = MaterialTheme.typography.titleSmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    detail.body,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Start,
                )
            }

            when (message) {
                ReviewMessage.RESOLVED -> Text(
                    stringResource(R.string.review_resolved),
                    color = MaterialTheme.colorScheme.primary,
                )
                ReviewMessage.STILL_NEEDS_REVIEW -> Text(
                    stringResource(R.string.review_still_pending),
                    color = MaterialTheme.colorScheme.error,
                )
                null -> Unit
            }
            if (error == ReviewError.ACTION_FAILED) {
                Text(stringResource(R.string.review_action_failed), color = MaterialTheme.colorScheme.error)
                actionErrorDetail?.let { reason ->
                    val detailRes = ReviewReasonLabels.labelRes(reason)
                    Text(
                        detailRes?.let { stringResource(it) } ?: reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (detail.showDismissNonFinancialAction) {
                Button(
                    onClick = onDismissNonFinancial,
                    enabled = !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.review_action_dismiss_non_financial))
                }
            }

            if (detail.showFinancialTypeActions) {
                Text(stringResource(R.string.review_actions_title), style = MaterialTheme.typography.titleSmall)
                REVIEW_FINANCIAL_TYPE_ACTIONS.forEach { type ->
                    Button(
                        onClick = { onResolveType(type) },
                        enabled = !resolving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(type.toUiLabelRes()))
                    }
                }
            }

            if (detail.showExternalTransferAction) {
                Button(
                    onClick = onResolveExternal,
                    enabled = !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.review_action_external_transfer))
                }
            }

            if (detail.showIncomingIncomeAction) {
                Button(
                    onClick = { onResolveType(FinancialTransactionType.INCOME) },
                    enabled = !resolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.review_action_incoming_income))
                }
            }

            if (detail.pairCandidates.isNotEmpty()) {
                Text(stringResource(R.string.review_pair_candidates), style = MaterialTheme.typography.titleSmall)
                detail.pairCandidates.forEach { candidate ->
                    Button(
                        onClick = { onResolvePair(candidate.id) },
                        enabled = !resolving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(stringResource(R.string.review_pair_with, candidate.title))
                            candidate.amountLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            if (resolving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
