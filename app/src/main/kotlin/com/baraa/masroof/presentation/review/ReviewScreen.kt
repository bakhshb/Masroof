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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.presentation.common.formatCardLast4
import com.baraa.masroof.presentation.common.CardOwnershipInlinePrompt
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.IconLabelRow
import com.baraa.masroof.presentation.common.IconTextButton
import com.baraa.masroof.presentation.common.MasroofCard
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold
import com.baraa.masroof.presentation.common.SectionHeader

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
            onDismissNonFinancial = viewModel::resolveAsIgnored,
            onConfirmOwnershipCardOwned = viewModel::confirmOwnershipCardOwned,
            onMarkOwnershipCardExternal = viewModel::markOwnershipCardExternal,
            onRestoreIgnored = viewModel::restoreIgnoredMessage,
        )
    } else {
        ReviewListScreen(
            state = state,
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onOpen = viewModel::openDetail,
            onDismissAllInformational = viewModel::dismissAllInformational,
            onListModeChange = viewModel::setListMode,
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
    onDismissAllInformational: () -> Unit,
    onListModeChange: (ReviewListMode) -> Unit,
) {
    MasroofSecondaryScaffold(
        title = stringResource(R.string.review_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.review_back),
    ) { contentModifier ->
        Column(modifier = contentModifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.listMode == ReviewListMode.PENDING,
                    onClick = { onListModeChange(ReviewListMode.PENDING) },
                    label = { Text(stringResource(R.string.review_tab_pending)) },
                    enabled = !state.loading || state.items.isNotEmpty(),
                )
                FilterChip(
                    selected = state.listMode == ReviewListMode.IGNORED,
                    onClick = { onListModeChange(ReviewListMode.IGNORED) },
                    label = { Text(stringResource(R.string.review_tab_ignored)) },
                    enabled = !state.loading || state.items.isNotEmpty(),
                )
            }
            when {
            state.loading && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error == ReviewError.LOAD_FAILED && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MasroofIcons.error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.review_load_error),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    IconTextButton(
                        onClick = onRefresh,
                        icon = MasroofIcons.retry,
                        text = stringResource(R.string.dashboard_retry),
                    )
                }
            }
            state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = MasroofIcons.success,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            if (state.listMode == ReviewListMode.IGNORED) {
                                R.string.review_ignored_empty
                            } else {
                                R.string.review_empty
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    IconTextButton(
                        onClick = onBack,
                        icon = MasroofIcons.backToCurrent,
                        text = stringResource(R.string.review_back),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SectionHeader(
                            title = stringResource(
                                if (state.listMode == ReviewListMode.IGNORED) {
                                    R.string.review_ignored_count
                                } else {
                                    R.string.review_count
                                },
                                state.items.size,
                            ),
                            icon = MasroofIcons.reviewQueue,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (state.listMode == ReviewListMode.PENDING && state.informationalDismissCount > 0) {
                        item {
                            IconTextButton(
                                onClick = onDismissAllInformational,
                                enabled = !state.resolving,
                                icon = MasroofIcons.warning,
                                text = stringResource(
                                    R.string.review_dismiss_all_informational,
                                    state.informationalDismissCount,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
}

@Composable
private fun ReviewListCard(item: ReviewListItemUi, onClick: () -> Unit) {
    MasroofCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = MasroofIcons.reviewKind(item.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    item.amountLabel?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    stringResource(item.kindLabelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.ignored) {
                            MasroofIcons.success
                        } else {
                            MasroofIcons.error
                        },
                        contentDescription = null,
                        tint = if (item.ignored) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    val reasonRes = ReviewReasonLabels.labelRes(item.reasonLabel)
                    Text(
                        reasonRes?.let { stringResource(it) } ?: item.reasonLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.ignored) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
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
    onConfirmOwnershipCardOwned: () -> Unit,
    onMarkOwnershipCardExternal: () -> Unit,
    onRestoreIgnored: (FinancialTransactionType?) -> Unit,
) {
    MasroofSecondaryScaffold(
        title = stringResource(R.string.review_detail_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.review_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MasroofCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MasroofIcons.messageFamily(detail.messageFamily),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(detail.kindLabelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                detail.amountLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            detail.sender?.let {
                IconLabelRow(icon = MasroofIcons.sender, label = stringResource(R.string.review_sender, it))
            }
            IconLabelRow(
                icon = MasroofIcons.calendar,
                label = stringResource(R.string.review_date, detail.dateLabel),
            )
            detail.resolvedAtLabel?.let {
                IconLabelRow(
                    icon = MasroofIcons.success,
                    label = stringResource(R.string.review_ignored_at, it),
                )
            }
            if (detail.showRestoreActions) {
                MasroofCard {
                    Text(
                        stringResource(R.string.review_restore_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionHeader(
                    title = stringResource(R.string.review_restore_actions_title),
                    icon = MasroofIcons.reviewQueue,
                )
                IconTextButton(
                    onClick = { onRestoreIgnored(null) },
                    enabled = !resolving,
                    icon = MasroofIcons.success,
                    text = stringResource(R.string.review_action_restore),
                    modifier = Modifier.fillMaxWidth(),
                )
                REVIEW_FINANCIAL_TYPE_ACTIONS.forEach { type ->
                    IconTextButton(
                        onClick = { onRestoreIgnored(type) },
                        enabled = !resolving,
                        icon = MasroofIcons.transactionType(type),
                        text = stringResource(R.string.review_action_restore_as, stringResource(type.toUiLabelRes())),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            detail.merchant?.let {
                IconLabelRow(icon = MasroofIcons.merchant, label = stringResource(R.string.review_merchant, it))
            }
            detail.counterparty?.let {
                IconLabelRow(icon = MasroofIcons.counterparty, label = stringResource(R.string.review_counterparty, it))
            }

            SectionHeader(
                title = stringResource(R.string.review_reasons_title),
                icon = MasroofIcons.warning,
            )
            detail.reasonLabels.forEach { reason ->
                val reasonRes = ReviewReasonLabels.labelRes(reason)
                Text(
                    "• ${reasonRes?.let { stringResource(it) } ?: reason}",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (detail.showOwnershipActions && detail.ownershipCard?.last4 != null) {
                SectionHeader(
                    title = stringResource(R.string.review_ownership_prompt_title),
                    icon = MasroofIcons.ownership,
                )
                Text(
                    stringResource(
                        R.string.review_ownership_prompt_body,
                        formatCardLast4(detail.ownershipCard.last4),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                CardOwnershipInlinePrompt(
                    enabled = !resolving,
                    onConfirmOwned = onConfirmOwnershipCardOwned,
                    onMarkExternal = onMarkOwnershipCardExternal,
                )
            }

            SectionHeader(
                title = stringResource(R.string.review_sms_body),
                icon = MasroofIcons.sms,
            )
            MasroofCard {
                Text(
                    detail.body,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (detail.showDismissNonFinancialAction) {
                IconTextButton(
                    onClick = onDismissNonFinancial,
                    enabled = !resolving,
                    icon = MasroofIcons.warning,
                    text = stringResource(R.string.review_action_ignore),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (detail.showFinancialTypeActions) {
                SectionHeader(
                    title = stringResource(R.string.review_actions_title),
                    icon = MasroofIcons.reviewQueue,
                )
                REVIEW_FINANCIAL_TYPE_ACTIONS.forEach { type ->
                    IconTextButton(
                        onClick = { onResolveType(type) },
                        enabled = !resolving,
                        icon = MasroofIcons.transactionType(type),
                        text = stringResource(type.toUiLabelRes()),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (detail.showExternalTransferAction) {
                val externalIcon = when (detail.messageFamily) {
                    MessageFamily.TRANSFER_IN -> MasroofIcons.externalIn
                    MessageFamily.TRANSFER_OUT -> MasroofIcons.externalOut
                    else -> MasroofIcons.selfTransfer
                }
                IconTextButton(
                    onClick = onResolveExternal,
                    enabled = !resolving,
                    icon = externalIcon,
                    text = stringResource(
                        when (detail.messageFamily) {
                            MessageFamily.TRANSFER_IN -> R.string.review_action_external_transfer_in
                            MessageFamily.TRANSFER_OUT -> R.string.review_action_external_transfer_out
                            else -> R.string.review_action_external_transfer
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (detail.showIncomingIncomeAction) {
                IconTextButton(
                    onClick = { onResolveType(FinancialTransactionType.INCOME) },
                    enabled = !resolving,
                    icon = MasroofIcons.income,
                    text = stringResource(R.string.review_action_incoming_income),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (detail.pairCandidates.isNotEmpty()) {
                SectionHeader(
                    title = stringResource(R.string.review_pair_candidates),
                    icon = MasroofIcons.pairMatch,
                )
                detail.pairCandidates.forEach { candidate ->
                    IconTextButton(
                        onClick = { onResolvePair(candidate.id) },
                        enabled = !resolving,
                        icon = MasroofIcons.pairMatch,
                        text = stringResource(R.string.review_pair_with, candidate.title),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (message) {
                ReviewMessage.RESOLVED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MasroofIcons.success,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.review_resolved),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ReviewMessage.STILL_NEEDS_REVIEW -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MasroofIcons.warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.review_still_pending),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                ReviewMessage.RESTORED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MasroofIcons.success,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.review_restored),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                null -> Unit
            }
            if (error == ReviewError.ACTION_FAILED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MasroofIcons.error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.review_action_failed), color = MaterialTheme.colorScheme.error)
                }
                actionErrorDetail?.let { reason ->
                    val detailRes = restoreFailureLabelRes(reason) ?: ReviewReasonLabels.labelRes(reason)
                    Text(
                        detailRes?.let { stringResource(it) } ?: reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (resolving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

private fun restoreFailureLabelRes(reason: String): Int? =
    when (reason) {
        "not_ignored", "review_not_found" -> R.string.settings_restore_not_ignored
        "reconcile_failed" -> R.string.settings_restore_reconcile_failed
        "review_clear_failed", "rollback_delete_failed", "rollback_review_failed" ->
            R.string.settings_restore_failed
        else -> null
    }
