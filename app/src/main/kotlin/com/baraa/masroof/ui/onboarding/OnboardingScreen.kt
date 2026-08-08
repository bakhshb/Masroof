package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

private fun isReadSmsGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repository: OnboardingRepository,
    permissionStore: SmsPermissionStore,
    onStepCompleted: (OnboardingStep) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val uiState = rememberSaveable(saver = OnboardingSaver) { UiOnboardingState() }
    var hydrated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!uiState.restoredFromSaver) {
            val draft = repository.loadDraft()
            if (draft != null) {
                val persistedAccount = draft.createdAccountId
                    .takeIf { it > 0L }
                    ?.let { app.financialAccountRepository.getById(it) }
                val recoveredAccount = if (
                    draft.onboardingVersion < CURRENT_ONBOARDING_VERSION &&
                    persistedAccount == null
                ) {
                    app.financialAccountRepository.getOwnedActive().firstOrNull()
                } else {
                    persistedAccount
                }
                val migrated = when {
                    draft.createdAccountId > 0L && persistedAccount == null ->
                        draft.copy(step = OnboardingStep.ACCOUNT, createdAccountId = 0L)
                    recoveredAccount != null && draft.onboardingVersion < CURRENT_ONBOARDING_VERSION ->
                        draft.copy(
                            step = if (draft.selectedSenderProfileId > 0L) {
                                OnboardingStep.COMPLETION
                            } else {
                                OnboardingStep.SELECT_SENDER
                            },
                            createdAccountId = recoveredAccount.id,
                            displayName = recoveredAccount.displayName,
                            institution = recoveredAccount.institutionName.orEmpty(),
                            accountType = recoveredAccount.accountType,
                            currency = recoveredAccount.currency,
                            openingBalance = recoveredAccount.openingBalance.toPlainString(),
                            includeNetWorth = recoveredAccount.includeInNetWorth,
                            includeLiquidity = recoveredAccount.includeInLiquidity,
                        )
                    else -> draft
                }
                uiState.restoreDraft(migrated)
            } else {
                val pending = repository.snapshot() as? OnboardingState.Pending
                if (pending != null && pending.onboardingVersion < CURRENT_ONBOARDING_VERSION) {
                    val existing = app.financialAccountRepository.getOwnedActive().firstOrNull()
                    if (existing == null) {
                        uiState.step = OnboardingStep.ACCOUNT
                    } else {
                        uiState.createdAccountId = existing.id
                        uiState.displayName = existing.displayName
                        uiState.accountType = existing.accountType
                        uiState.institution = existing.institutionName.orEmpty()
                        uiState.currency = existing.currency
                        uiState.openingBalance = existing.openingBalance.toPlainString()
                        uiState.includeNetWorth = existing.includeInNetWorth
                        uiState.includeLiquidity = existing.includeInLiquidity
                        uiState.step = OnboardingStep.SELECT_SENDER
                    }
                } else {
                    uiState.step = pending?.lastCompletedStep
                        ?.let(::nextOnboardingStep)
                        ?: OnboardingStep.WELCOME
                }
            }
        }
        hydrated = true
    }
    LaunchedEffect(hydrated) {
        if (!hydrated) return@LaunchedEffect
        snapshotFlow { uiState.toDraft() }
            .distinctUntilChanged()
            .collect { draft -> repository.saveDraft(draft) }
    }

    var permissionGranted by remember { mutableStateOf(isReadSmsGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    var permanentlyDenied by remember { mutableStateOf(false) }
    var resumeGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStore.refresh()
                permissionGranted = isReadSmsGranted(context)
                resumeGeneration += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionStore.refresh()
        if (!granted && activity != null) {
            permanentlyDenied = !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)
        }
    }

    fun advance(from: OnboardingStep, to: OnboardingStep) {
        // Navigation must not depend on a best-effort persistence callback succeeding.
        uiState.step = to
        runCatching { repository.saveDraft(uiState.toDraft()) }
        onStepCompleted(from)
    }

    fun navigateBack() {
        uiState.step = previousOnboardingStep(uiState.step)
    }

    BackHandler(enabled = uiState.step != OnboardingStep.WELCOME, onBack = ::navigateBack)

    Scaffold(
        topBar = {
            MasroofTopAppBar(
                title = "إعداد مصروف",
                onBack = if (uiState.step == OnboardingStep.WELCOME) null else ::navigateBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.x4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            LinearProgressIndicator(
                progress = { progressOf(uiState.step) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "الخطوة ${uiState.step.ordinal + 1} من ${OnboardingStep.entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (uiState.step) {
                OnboardingStep.WELCOME -> WelcomeAccountFirstStep(
                    onContinue = { advance(OnboardingStep.WELCOME, OnboardingStep.ACCOUNT) },
                )
                OnboardingStep.ACCOUNT -> AccountSetupStep(
                    app = app,
                    state = uiState,
                    repository = repository,
                    onContinue = { advance(OnboardingStep.ACCOUNT, OnboardingStep.SELECT_SENDER) },
                )
                OnboardingStep.SELECT_SENDER -> SelectSenderStep(
                    app = app,
                    state = uiState,
                    permissionGranted = permissionGranted,
                    permanentlyDenied = permanentlyDenied,
                    resumeGeneration = resumeGeneration,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    onContinue = {
                        advance(OnboardingStep.SELECT_SENDER, OnboardingStep.COMPLETION)
                    },
                )
                OnboardingStep.COMPLETION -> CompletionStep(
                    app = app,
                    state = uiState,
                    repository = repository,
                    onFinish = onFinished,
                )
            }
        }
    }
}

private fun progressOf(step: OnboardingStep): Float = when (step) {
    OnboardingStep.WELCOME -> 0.25f
    OnboardingStep.ACCOUNT -> 0.5f
    OnboardingStep.SELECT_SENDER -> 0.75f
    OnboardingStep.COMPLETION -> 1f
}

@Composable
internal fun WelcomeAccountFirstStep(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مرحباً بك في مصروف", style = MaterialTheme.typography.headlineSmall)
        Text(
            "أنشئ حسابك واربط مرسل رسائله. يمكنك مراجعة أنماط البنك لاحقاً من «رسائل البنوك».",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryButton("متابعة", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun CompletionStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    repository: OnboardingRepository,
    onFinish: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تم إعداد مصروف", style = MaterialTheme.typography.titleLarge)
        Text("الحساب: ${state.displayName}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "مرسل الرسائل: ${state.selectedSenderDisplay.ifBlank { "سيتم ربطه لاحقاً" }}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "راجع أنماط رسائل البنك قبل أول استيراد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            if (saving) "جارٍ الإعداد…" else "بدء استخدام مصروف",
            enabled = !saving && state.createdAccountId > 0L,
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    runCatching {
                        completeMinimalOnboarding(
                            accountId = state.createdAccountId,
                            accountExists = { id ->
                                app.financialAccountRepository.getById(id) != null
                            },
                            saveFinancialSetup = {
                                app.financialSetupRepository.save(
                                    setupFrom(state, completed = true),
                                )
                            },
                            markCompleted = repository::markCompleted,
                        )
                    }.onSuccess {
                        onFinish()
                    }.onFailure {
                        error = "تعذر إكمال الإعداد. حاول مرة أخرى."
                    }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun setupFrom(state: UiOnboardingState, completed: Boolean = false) =
    com.baraa.masroof.data.repository.FinancialSetup(
        trackingStartDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        setupCompleted = completed,
        setupCompletedAt = if (completed) System.currentTimeMillis() else 0L,
        defaultCurrency = state.currency,
    )
