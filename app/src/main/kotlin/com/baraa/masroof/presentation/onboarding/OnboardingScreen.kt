package com.baraa.masroof.presentation.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.OwnershipStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingScreen(
        state = state,
        onStart = viewModel::onStartClicked,
        onRequestPermissions = onRequestPermissions,
        onOpenAppSettings = onOpenAppSettings,
        onSelectDateOption = viewModel::selectDateOption,
        onSelectCustomDate = viewModel::selectCustomDate,
        onStartImport = viewModel::startImport,
        onSetAccountOwned = { c -> viewModel.setAccountOwnership(c, true) },
        onSetAccountExternal = { c -> viewModel.setAccountOwnership(c, false) },
        onSetCardOwned = { c -> viewModel.setCardOwnership(c, true) },
        onSetCardExternal = { c -> viewModel.setCardOwnership(c, false) },
        onFinalize = viewModel::finalizeOnboarding,
        onEnterApp = viewModel::enterApp,
    )
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onStart: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSelectDateOption: (ImportDateOption) -> Unit,
    onSelectCustomDate: (LocalDate) -> Unit,
    onStartImport: () -> Unit,
    onSetAccountOwned: (OwnershipCandidateUi) -> Unit,
    onSetAccountExternal: (OwnershipCandidateUi) -> Unit,
    onSetCardOwned: (OwnershipCandidateUi) -> Unit,
    onSetCardExternal: (OwnershipCandidateUi) -> Unit,
    onFinalize: () -> Unit,
    onEnterApp: () -> Unit,
) {
    Scaffold { padding ->
        when (state.step) {
            OnboardingStep.WELCOME -> WelcomeStep(Modifier.padding(padding), onStart)
            OnboardingStep.PERMISSION -> PermissionStep(
                modifier = Modifier.padding(padding),
                denied = state.error == OnboardingError.PERMISSION_DENIED,
                onRequestPermissions = onRequestPermissions,
                onOpenSettings = onOpenAppSettings,
            )
            OnboardingStep.IMPORT_DATE -> ImportDateStep(
                modifier = Modifier.padding(padding),
                state = state,
                onSelectDateOption = onSelectDateOption,
                onSelectCustomDate = onSelectCustomDate,
                onContinue = onStartImport,
            )
            OnboardingStep.IMPORTING -> ImportingStep(Modifier.padding(padding), state, onStartImport)
            OnboardingStep.OWNERSHIP -> OwnershipStep(
                modifier = Modifier.padding(padding),
                state = state,
                onSetAccountOwned = onSetAccountOwned,
                onSetAccountExternal = onSetAccountExternal,
                onSetCardOwned = onSetCardOwned,
                onSetCardExternal = onSetCardExternal,
                onFinalize = onFinalize,
            )
            OnboardingStep.FINALIZE -> CompletionStep(Modifier.padding(padding), state, onEnterApp)
            OnboardingStep.HOME -> HomePlaceholder(Modifier.padding(padding), state)
        }
    }
}

@Composable
private fun WelcomeStep(modifier: Modifier, onStart: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_welcome_body))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_welcome_local_only))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

@Composable
private fun PermissionStep(
    modifier: Modifier,
    denied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_permission_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_permission_body))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_allow))
        }
        if (denied) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_open_settings))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImportDateStep(
    modifier: Modifier,
    state: OnboardingUiState,
    onSelectDateOption: (ImportDateOption) -> Unit,
    onSelectCustomDate: (LocalDate) -> Unit,
    onContinue: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val selected = state.selectedImportDate ?: LocalDate.now()

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.onboarding_import_date_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        DateOptionRow(state.selectedDateOption == ImportDateOption.CURRENT_MONTH_START, stringResource(R.string.onboarding_date_current_month)) {
            onSelectDateOption(ImportDateOption.CURRENT_MONTH_START)
        }
        DateOptionRow(state.selectedDateOption == ImportDateOption.LAST_30_DAYS, stringResource(R.string.onboarding_date_last_30)) {
            onSelectDateOption(ImportDateOption.LAST_30_DAYS)
        }
        DateOptionRow(state.selectedDateOption == ImportDateOption.LAST_27TH, stringResource(R.string.onboarding_date_last_27)) {
            onSelectDateOption(ImportDateOption.LAST_27TH)
        }
        DateOptionRow(state.selectedDateOption == ImportDateOption.CUSTOM, stringResource(R.string.onboarding_date_custom)) {
            onSelectDateOption(ImportDateOption.CUSTOM)
            showDatePicker = true
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_selected_date, selected.toString()))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_start_import))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onSelectCustomDate(date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateOptionRow(selected: Boolean, title: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ImportingStep(modifier: Modifier, state: OnboardingUiState, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (val importState = state.importState) {
            is ImportState.Scanning, ImportState.Idle -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.onboarding_import_scanning))
            }
            is ImportState.Completed -> {
                Text(stringResource(R.string.onboarding_import_done))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onboarding_import_counts, importState.result.scanned, importState.result.parsed, importState.result.duplicates, importState.result.failed))
            }
            is ImportState.PermissionError -> {
                Text(stringResource(R.string.onboarding_permission_denied_short))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.onboarding_retry)) }
            }
            is ImportState.ProviderError -> {
                Text(stringResource(R.string.onboarding_provider_error))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.onboarding_retry)) }
            }
        }
    }
}

@Composable
private fun OwnershipStep(
    modifier: Modifier,
    state: OnboardingUiState,
    onSetAccountOwned: (OwnershipCandidateUi) -> Unit,
    onSetAccountExternal: (OwnershipCandidateUi) -> Unit,
    onSetCardOwned: (OwnershipCandidateUi) -> Unit,
    onSetCardExternal: (OwnershipCandidateUi) -> Unit,
    onFinalize: () -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.onboarding_ownership_title), style = MaterialTheme.typography.headlineSmall)
            if (state.hasUnknownCandidates) {
                Text(stringResource(R.string.onboarding_ownership_must_resolve))
            }
        }
        item { Text(stringResource(R.string.onboarding_accounts_section), style = MaterialTheme.typography.titleMedium) }
        items(state.accounts) { candidate ->
            CandidateCard(
                candidate = candidate,
                ownedLabel = stringResource(R.string.onboarding_is_mine_account),
                externalLabel = stringResource(R.string.onboarding_not_mine),
                onOwned = { onSetAccountOwned(candidate) },
                onExternal = { onSetAccountExternal(candidate) },
            )
        }
        item { Text(stringResource(R.string.onboarding_cards_section), style = MaterialTheme.typography.titleMedium) }
        items(state.cards) { candidate ->
            CandidateCard(
                candidate = candidate,
                ownedLabel = stringResource(R.string.onboarding_is_mine_card),
                externalLabel = stringResource(R.string.onboarding_not_mine),
                onOwned = { onSetCardOwned(candidate) },
                onExternal = { onSetCardExternal(candidate) },
            )
        }
        item {
            Button(
                onClick = onFinalize,
                enabled = !state.hasUnknownCandidates && !state.finalizing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_finalize))
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: OwnershipCandidateUi,
    ownedLabel: String,
    externalLabel: String,
    onOwned: () -> Unit,
    onExternal: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (candidate.bank == Bank.BANK_ALJAZIRA) stringResource(R.string.bank_aljazira)
                else stringResource(R.string.bank_unknown),
            )
            val label = if (candidate.kind == OwnershipCandidateUi.CandidateKind.ACCOUNT) {
                stringResource(R.string.onboarding_account_suffix, candidate.suffix)
            } else {
                stringResource(R.string.onboarding_card_suffix, candidate.suffix)
            }
            Text(label)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOwned) { Text(ownedLabel) }
                Button(onClick = onExternal) { Text(externalLabel) }
            }
            Text(
                when (candidate.ownership) {
                    OwnershipStatus.OWNED -> stringResource(R.string.onboarding_status_owned)
                    OwnershipStatus.EXTERNAL -> stringResource(R.string.onboarding_status_external)
                    OwnershipStatus.UNKNOWN -> stringResource(R.string.onboarding_status_unknown)
                },
            )
        }
    }
}

@Composable
private fun CompletionStep(modifier: Modifier, state: OnboardingUiState, onEnterApp: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_done_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_done_counts, state.ownedAccountsCount, state.ownedCardsCount, state.reviewRequiredCount))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onEnterApp, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_enter_app))
        }
    }
}

@Composable
private fun HomePlaceholder(modifier: Modifier, state: OnboardingUiState) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_setup_complete))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_done_counts, state.ownedAccountsCount, state.ownedCardsCount, state.reviewRequiredCount))
    }
}
