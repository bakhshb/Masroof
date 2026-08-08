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
import com.baraa.masroof.ui.theme.SecondaryButton
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
    val scope = rememberCoroutineScope()
    var hydrated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!uiState.restoredFromSaver) {
            val draft = repository.loadDraft()
            if (draft != null) {
                uiState.restoreDraft(draft)
            } else {
                val pending = repository.snapshot() as? OnboardingState.Pending
                uiState.step = pending?.lastCompletedStep
                    ?.let(::nextOnboardingStep)
                    ?: OnboardingStep.WELCOME
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
        if (granted) {
            scope.launch { onStepCompleted(OnboardingStep.PERMISSION) }
            uiState.step = OnboardingStep.SELECT_SENDER
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
                "الخطوة ${uiState.step.ordinal + 1} من ${OnboardingStep.values().size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (uiState.step) {
                OnboardingStep.WELCOME -> WelcomePatternFirstStep(
                    onContinue = { advance(OnboardingStep.WELCOME, OnboardingStep.PERMISSION) },
                )
                OnboardingStep.PERMISSION -> PermissionStep(
                    granted = permissionGranted,
                    permanentlyDenied = permanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                    onContinue = {
                        advance(OnboardingStep.PERMISSION, OnboardingStep.SELECT_SENDER)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                )
                OnboardingStep.SELECT_SENDER -> SelectSenderStep(
                    app = app,
                    state = uiState,
                    resumeGeneration = resumeGeneration,
                    onContinue = { advance(OnboardingStep.SELECT_SENDER, OnboardingStep.CREATE_PATTERN) },
                )
                OnboardingStep.CREATE_PATTERN -> CreatePatternStep(
                    app = app,
                    state = uiState,
                    resumeGeneration = resumeGeneration,
                    onSaved = { advance(OnboardingStep.CREATE_PATTERN, OnboardingStep.PATTERN_SUMMARY) },
                )
                OnboardingStep.PATTERN_SUMMARY -> PatternSummaryStep(
                    state = uiState,
                    onAddAnother = {
                        uiState.selectedSmsBody = null
                        uiState.draftTemplate = ""
                        advance(OnboardingStep.PATTERN_SUMMARY, OnboardingStep.CREATE_PATTERN)
                    },
                    onContinue = {
                        advance(OnboardingStep.PATTERN_SUMMARY, OnboardingStep.SENDER_PATTERN_SUMMARY)
                    },
                )
                OnboardingStep.SENDER_PATTERN_SUMMARY -> SenderPatternSummaryStep(
                    app = app,
                    state = uiState,
                    onContinue = { advance(OnboardingStep.SENDER_PATTERN_SUMMARY, OnboardingStep.ACCOUNT) },
                    onAddPattern = {
                        uiState.selectedSmsBody = null
                        uiState.draftTemplate = ""
                        uiState.step = OnboardingStep.CREATE_PATTERN
                    },
                )
                OnboardingStep.ACCOUNT -> AccountFromPatternsStep(
                    app = app,
                    state = uiState,
                    onContinue = { advance(OnboardingStep.ACCOUNT, OnboardingStep.IDENTIFIERS) },
                )
                OnboardingStep.IDENTIFIERS -> IdentifiersStep(
                    app = app,
                    state = uiState,
                    onContinue = { advance(OnboardingStep.IDENTIFIERS, OnboardingStep.IMPORT_PREVIEW) },
                )
                OnboardingStep.IMPORT_PREVIEW -> ImportPreviewStep(
                    app = app,
                    state = uiState,
                    resumeGeneration = resumeGeneration,
                    onContinue = { advance(OnboardingStep.IMPORT_PREVIEW, OnboardingStep.LINK_PREVIEW) },
                )
                OnboardingStep.LINK_PREVIEW -> LinkPreviewStep(
                    app = app,
                    state = uiState,
                    resumeGeneration = resumeGeneration,
                    onContinue = { advance(OnboardingStep.LINK_PREVIEW, OnboardingStep.IMPORT) },
                )
                OnboardingStep.IMPORT -> ImportCommitStep(
                    app = app,
                    state = uiState,
                    repository = repository,
                    resumeGeneration = resumeGeneration,
                    onFinished = onFinished,
                    onStepCompleted = onStepCompleted,
                )
                OnboardingStep.COMPLETION -> CompletionStep(onFinish = onFinished)
            }
        }
    }
}

private fun progressOf(step: OnboardingStep): Float = when (step) {
    OnboardingStep.WELCOME -> 0.05f
    OnboardingStep.PERMISSION -> 0.12f
    OnboardingStep.SELECT_SENDER -> 0.22f
    OnboardingStep.CREATE_PATTERN -> 0.32f
    OnboardingStep.PATTERN_SUMMARY -> 0.40f
    OnboardingStep.SENDER_PATTERN_SUMMARY -> 0.48f
    OnboardingStep.ACCOUNT -> 0.58f
    OnboardingStep.IDENTIFIERS -> 0.68f
    OnboardingStep.IMPORT_PREVIEW -> 0.78f
    OnboardingStep.LINK_PREVIEW -> 0.86f
    OnboardingStep.IMPORT -> 0.94f
    OnboardingStep.COMPLETION -> 1f
}

@Composable
internal fun WelcomePatternFirstStep(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مرحباً بك في مصروف", style = MaterialTheme.typography.headlineSmall)
        Text(
            "سنساعدك على تعريف رسائل البنك وإنشاء حسابك، ثم استيراد العمليات المطابقة فقط. تبقى الرسائل على جهازك.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryButton("متابعة", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun PermissionStep(
    granted: Boolean,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إذن قراءة الرسائل", style = MaterialTheme.typography.titleLarge)
        Text(
            "نحتاج الإذن لاكتشاف مرسلي البنوك وإنشاء الأنماط. لن نستورد عمليات مالية في هذه الخطوة.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            if (granted) "تم منح الإذن"
            else if (permanentlyDenied) "تم رفض الإذن — افتح الإعدادات لمنحه"
            else "لم يُمنح الإذن بعد",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!granted) {
            PrimaryButton("السماح بقراءة الرسائل", onClick = onRequest, modifier = Modifier.fillMaxWidth())
            if (permanentlyDenied) {
                SecondaryButton("فتح إعدادات التطبيق", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
            }
        } else {
            PrimaryButton("متابعة", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun CompletionStep(onFinish: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("جاهز للبدء", style = MaterialTheme.typography.titleLarge)
        Text(
            "يمكنك لاحقاً إضافة أنماط جديدة من «رسائل البنوك» عند ظهور أنواع رسائل غير معروفة.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryButton("بدء استخدام التطبيق", onClick = onFinish, modifier = Modifier.fillMaxWidth())
    }
}

internal fun setupFrom(state: UiOnboardingState, completed: Boolean = false) =
    com.baraa.masroof.data.repository.FinancialSetup(
        trackingStartDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        setupCompleted = completed,
        setupCompletedAt = if (completed) System.currentTimeMillis() else 0L,
        defaultCurrency = state.currency,
    )
