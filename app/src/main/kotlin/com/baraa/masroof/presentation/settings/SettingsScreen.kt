package com.baraa.masroof.presentation.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.presentation.common.BackNavigationIcon
import com.baraa.masroof.presentation.common.MasroofIcons

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    reviewRequiredCount: Int,
    onBack: () -> Unit,
    onOpenReview: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    val state by viewModel.uiState.collectAsState()
    var destination by rememberSaveable { mutableStateOf(SettingsDestination.Hub) }

    BackHandler(enabled = destination != SettingsDestination.Hub) {
        destination = SettingsDestination.Hub
    }

    when (destination) {
        SettingsDestination.Hub -> SettingsHubScreen(
            state = state,
            reviewRequiredCount = reviewRequiredCount,
            onBack = onBack,
            onOpenMyCards = { destination = SettingsDestination.MyCards },
            onOpenMyAccounts = { destination = SettingsDestination.MyAccounts },
            onOpenReview = onOpenReview,
            onOpenAbout = { destination = SettingsDestination.About },
            onReparseStored = viewModel::reparseStoredMessages,
        )

        SettingsDestination.MyCards -> SettingsMyCardsScreen(
            state = state,
            onBack = { destination = SettingsDestination.Hub },
            onConfirmOwned = viewModel::confirmCardOwned,
            onMarkExternal = viewModel::markCardExternal,
            onRequestStopTracking = viewModel::requestStopTracking,
            onResumeTracking = viewModel::resumeTracking,
            onDismissStopConfirm = viewModel::dismissStopConfirm,
            onConfirmStopTracking = viewModel::confirmStopTracking,
        )

        SettingsDestination.MyAccounts -> SettingsMyAccountsScreen(
            state = state,
            onBack = { destination = SettingsDestination.Hub },
            onConfirmOwned = viewModel::confirmAccountOwned,
            onMarkExternal = viewModel::markAccountExternal,
            onRequestStopTracking = viewModel::requestStopAccountTracking,
            onResumeTracking = viewModel::resumeAccountTracking,
            onDismissStopConfirm = viewModel::dismissStopConfirm,
            onConfirmStopTracking = viewModel::confirmStopAccountTracking,
        )

        SettingsDestination.About -> SettingsAboutScreen(
            appVersion = state.appVersion,
            onBack = { destination = SettingsDestination.Hub },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubScreen(
    state: SettingsUiState,
    reviewRequiredCount: Int,
    onBack: () -> Unit,
    onOpenMyCards: () -> Unit,
    onOpenMyAccounts: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenAbout: () -> Unit,
    onReparseStored: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    BackNavigationIcon(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsNavRow(
                icon = MasroofIcons.cardPayment,
                title = stringResource(R.string.settings_cards_section),
                subtitle = cardsHubSubtitle(state),
                onClick = onOpenMyCards,
            )

            SettingsNavRow(
                icon = MasroofIcons.externalIn,
                title = stringResource(R.string.settings_accounts_section),
                subtitle = accountsHubSubtitle(state),
                onClick = onOpenMyAccounts,
            )

            SettingsNavRow(
                icon = MasroofIcons.notifications,
                title = stringResource(R.string.settings_hub_review_title),
                subtitle = reviewHubSubtitle(reviewRequiredCount),
                badgeCount = reviewRequiredCount.takeIf { it > 0 },
                onClick = onOpenReview,
            )

            SettingsReparseRow(
                title = stringResource(R.string.settings_reparse_title),
                subtitle = stringResource(R.string.settings_reparse_stored_hint),
                detail = stringResource(R.string.settings_reparse_stored_example),
                icon = MasroofIcons.rescan,
                actionIcon = MasroofIcons.retry,
                running = state.reparsingStored,
                enabled = !state.reparsingStored && !state.updating,
                onRefresh = onReparseStored,
            )

            SettingsNavRow(
                icon = MasroofIcons.periodHint,
                title = stringResource(R.string.settings_about_section),
                subtitle = stringResource(R.string.settings_about_subtitle, state.appVersion),
                onClick = onOpenAbout,
            )

            state.error?.let {
                Text(
                    stringResource(R.string.settings_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun cardsHubSubtitle(state: SettingsUiState): String {
    val followed = state.followedCards.size
    val unregistered = state.unregisteredCards.size
    val stopped = state.stoppedCards.size
    return when {
        followed == 0 && unregistered == 0 && stopped == 0 ->
            stringResource(R.string.settings_hub_cards_subtitle_none)

        unregistered > 0 ->
            stringResource(R.string.settings_hub_cards_subtitle, followed, unregistered)

        else ->
            stringResource(R.string.settings_hub_cards_subtitle_followed_only, followed)
    }
}

@Composable
private fun accountsHubSubtitle(state: SettingsUiState): String {
    val followed = state.followedAccounts.size
    val unregistered = state.unregisteredAccounts.size
    val stopped = state.stoppedAccounts.size
    return when {
        followed == 0 && unregistered == 0 && stopped == 0 ->
            stringResource(R.string.settings_hub_accounts_subtitle_none)

        unregistered > 0 ->
            stringResource(R.string.settings_hub_accounts_subtitle, followed, unregistered)

        else ->
            stringResource(R.string.settings_hub_accounts_subtitle_followed_only, followed)
    }
}

@Composable
private fun reviewHubSubtitle(reviewRequiredCount: Int): String =
    if (reviewRequiredCount > 0) {
        stringResource(R.string.settings_hub_review_subtitle_count, reviewRequiredCount)
    } else {
        stringResource(R.string.settings_hub_review_subtitle_none)
    }
