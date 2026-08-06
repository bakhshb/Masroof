package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.ui.accounts.AccountSmsBindingDialog
import com.baraa.masroof.ui.theme.FinancialShapes
import com.baraa.masroof.ui.theme.MasroofTopAppBar
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import kotlinx.coroutines.launch

/**
 * Onboarding flow steps.
 *
 * The order is fixed; the user advances by triggering the next step
 * from the appropriate button. `PERMISSION` must complete before the
 * rest of the flow. Each `onStepCompleted` call persists the step in
 * the [OnboardingRepository] so a process death resumes here.
 */
enum class OnboardingStep {
    PERMISSION, WELCOME, START_DATE, ACCOUNT, OPENING_BALANCE, COMPLETION,
}

class UiOnboardingState {
    var step by mutableStateOf(OnboardingStep.PERMISSION)
    var option by mutableStateOf(StartDateOption.TODAY)
    var trackingDate by mutableStateOf(java.time.LocalDate.now())
    var accountType by mutableStateOf(com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT)
    var displayName by mutableStateOf("")
    var institution by mutableStateOf("")
    var lastFour by mutableStateOf("")
    var openingBalance by mutableStateOf("0")
    var currency by mutableStateOf(com.baraa.masroof.transaction.Currency.SAR)
    var includeLiquidity by mutableStateOf(true)
    var includeNetWorth by mutableStateOf(true)
    var skipped by mutableStateOf(false)
}

enum class StartDateOption { TODAY, MONTH_START, CUSTOM }

private fun isReadSmsGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

/**
 * Composable entry point for onboarding.
 *
 * Reads its truth from [OnboardingRepository]. The host (typically
 * [com.baraa.masroof.MainActivity]) is responsible for placing this
 * composable inside the NavHost only when the persistent state says
 * the onboarding is not yet completed.
 *
 * `onFinished` is fired exactly once per session and **only** after
 *   1. [OnboardingRepository.markCompleted] has persisted
 *      onboardingCompleted=true,
 *   2. the caller has navigated to the main destination.
 */
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
    var pendingSmsBindingAccount by remember { mutableStateOf<com.baraa.masroof.data.db.FinancialAccount?>(null) }

    // Resume at the previously persisted step rather than restarting
    // from WELCOME on every process recreation.
    val persistentState by repository.observe().collectAsStateWithLifecycle(initialValue = OnboardingState.Loading)
    LaunchedEffect(persistentState) {
        val pending = persistentState as? OnboardingState.Pending ?: return@LaunchedEffect
        val resumeStep = pending.lastCompletedStep?.let { nextStep(it) } ?: OnboardingStep.PERMISSION
        if (uiState.step == OnboardingStep.PERMISSION && resumeStep != OnboardingStep.PERMISSION) {
            uiState.step = resumeStep
        }
    }

    var permissionGranted by remember { mutableStateOf(isReadSmsGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    // Re-check on every ON_RESUME so a grant from Android Settings is
    // observed even when the user returns to the app without restarting
    // it. `permissionStore.refresh()` updates the single source of
    // truth; the local mirror re-derives from `ContextCompat`.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = isReadSmsGranted(context)
                permissionStore.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) {
            scope.launch { onStepCompleted(OnboardingStep.PERMISSION) }
            uiState.step = OnboardingStep.WELCOME
        }
    }

    Scaffold(topBar = { MasroofTopAppBar(title = "إعداد مصروف") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LinearProgressIndicator(progress = progress(uiState.step), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            when (uiState.step) {
                OnboardingStep.PERMISSION -> PermissionStep(
                    granted = permissionGranted,
                    permanentlyDenied = !permissionGranted && (activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false),
                    onRequest = { launcher.launch(Manifest.permission.READ_SMS) },
                    onContinue = {
                        scope.launch {
                            onStepCompleted(OnboardingStep.PERMISSION)
                            uiState.step = OnboardingStep.WELCOME
                        }
                    },
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                )
                OnboardingStep.WELCOME -> WelcomeStep(onContinue = {
                    scope.launch {
                        onStepCompleted(OnboardingStep.WELCOME)
                        uiState.step = OnboardingStep.START_DATE
                    }
                })
                OnboardingStep.START_DATE -> StartDateStep(uiState) {
                    scope.launch {
                        onStepCompleted(OnboardingStep.START_DATE)
                        uiState.step = OnboardingStep.ACCOUNT
                    }
                }
                OnboardingStep.ACCOUNT -> AccountStep(uiState) {
                    scope.launch {
                        onStepCompleted(OnboardingStep.ACCOUNT)
                        uiState.step = OnboardingStep.OPENING_BALANCE
                    }
                }
                OnboardingStep.OPENING_BALANCE -> OpeningBalanceStep(uiState) {
                    scope.launch {
                        onStepCompleted(OnboardingStep.OPENING_BALANCE)
                        uiState.step = OnboardingStep.COMPLETION
                    }
                }
                OnboardingStep.COMPLETION -> CompletionStep(app, uiState, onFinish = {
                    scope.launch {
                        val accountId = persistOnboardingAccount(app, uiState)
                        if (accountId == null) {
                            // Account insert failed; do NOT mark onboarding complete.
                            return@launch
                        }
                        // Verify the account is actually in Room by re-loading it.
                        val reloaded = app.financialAccountRepository.getById(accountId)
                        if (reloaded == null) {
                            return@launch
                        }
                        // Offer selected-SMS binding. The account is already
                        // persisted, so the user can confirm a typed identifier
                        // or skip without losing the account.
                        pendingSmsBindingAccount = reloaded
                    }
                })
            }
        }
    }
    pendingSmsBindingAccount?.let { account ->
        AccountSmsBindingDialog(
            accountId = account.id,
            accountType = account.accountType,
            onDismiss = {
                pendingSmsBindingAccount = null
                scope.launch {
                    app.financialSetupRepository.save(setupFrom(uiState, completed = true))
                    repository.markCompleted()
                    onFinished()
                }
            },
        )
    }
}

private fun progress(step: OnboardingStep) = when (step) {
    OnboardingStep.PERMISSION -> 0.05f
    OnboardingStep.WELCOME -> 0.20f
    OnboardingStep.START_DATE -> 0.40f
    OnboardingStep.ACCOUNT -> 0.55f
    OnboardingStep.OPENING_BALANCE -> 0.75f
    OnboardingStep.COMPLETION -> 1f
}

/** Returns the next step that should be shown after [completed]. */
private fun nextStep(completed: OnboardingStep): OnboardingStep = when (completed) {
    OnboardingStep.PERMISSION -> OnboardingStep.WELCOME
    OnboardingStep.WELCOME -> OnboardingStep.START_DATE
    OnboardingStep.START_DATE -> OnboardingStep.ACCOUNT
    OnboardingStep.ACCOUNT -> OnboardingStep.OPENING_BALANCE
    OnboardingStep.OPENING_BALANCE -> OnboardingStep.COMPLETION
    OnboardingStep.COMPLETION -> OnboardingStep.COMPLETION
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(shape = FinancialShapes.pill, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp).size(36.dp),
            )
        }
        Text("مرحبًا بك في مصروف", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("مصروف يساعدك على فهم أموالك تلقائيًا من رسائل البنك، دون الحاجة إلى إدخال كل عملية يدويًا.", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(label = "ابدأ الإعداد", onClick = onContinue)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateStep(state: UiOnboardingState, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("متى تبدأ المتابعة؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val choices = listOf(StartDateOption.TODAY to "من اليوم", StartDateOption.MONTH_START to "من بداية هذا الشهر", StartDateOption.CUSTOM to "اختيار تاريخ سابق")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (opt, label) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (state.option == opt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    color = if (state.option == opt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    onClick = {
                        state.option = opt
                        state.trackingDate = when (opt) {
                            StartDateOption.TODAY -> java.time.LocalDate.now()
                            StartDateOption.MONTH_START -> java.time.YearMonth.now().atDay(1)
                            StartDateOption.CUSTOM -> state.trackingDate
                        }
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.option == opt, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        if (state.option == StartDateOption.CUSTOM) {
            OutlinedTextField(
                value = state.trackingDate.toString(),
                onValueChange = { /* no-op: read-only */ },
                enabled = false,
                readOnly = true,
                label = { Text("تاريخ مخصص (يظهر هنا، قابل للتعديل بعد الإعداد)") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("يمكنك تعديل هذا التاريخ لاحقًا من شاشة السجل المالي.") },
            )
        }
        Text(if (state.option == StartDateOption.TODAY) "أدخل رصيدك الحالي لكل حساب." else "أدخل الرصيد الذي كان موجودًا في بداية التاريخ المختار، وليس رصيدك الحالي.", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(label = "متابعة", onClick = onNext)
    }
}

@Composable
private fun AccountStep(state: UiOnboardingState, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("أضف حسابك الأساسي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val presets = listOf(
            com.baraa.masroof.transaction.AccountType.BANK_ACCOUNT to "حساب الراتب",
            com.baraa.masroof.transaction.AccountType.CREDIT_CARD to "بطاقة ائتمانية",
            com.baraa.masroof.transaction.AccountType.DIGITAL_WALLET to "محفظة رقمية",
            com.baraa.masroof.transaction.AccountType.CASH to "نقد",
        )
        presets.forEach { (type, label) -> AssistChip(onClick = { state.accountType = type; state.displayName = label; state.includeLiquidity = com.baraa.masroof.transaction.AccountLiquidityDefaults.defaultFor(type); state.includeNetWorth = true }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
        PrimaryButton(label = "متابعة", enabled = state.displayName.isNotBlank(), onClick = onNext)
    }
}

@Composable
private fun OpeningBalanceStep(state: UiOnboardingState, onNext: () -> Unit) {
    val isLiability = com.baraa.masroof.transaction.AccountNature.defaultNatureFor(state.accountType) == com.baraa.masroof.transaction.AccountNature.LIABILITY
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("الرصيد الافتتاحي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(if (isLiability) "أدخل المبلغ المستحق عليك كرقم موجب." else "أدخل المبلغ الموجود في الحساب.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(state.displayName, { state.displayName = it }, label = { Text("اسم الحساب") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), isError = state.displayName.isBlank())
        OutlinedTextField(state.institution, { state.institution = it }, label = { Text("المؤسسة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.lastFour.take(4), { if (it.length <= 4 && it.all(Char::isDigit)) state.lastFour = it }, label = { Text("آخر 4 أرقام") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.openingBalance, { if (it.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) state.openingBalance = it }, label = { Text("الرصيد") }, modifier = Modifier.fillMaxWidth(), isError = runCatching { java.math.BigDecimal(state.openingBalance) }.isFailure)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { com.baraa.masroof.transaction.Currency.values().filter { it != com.baraa.masroof.transaction.Currency.UNKNOWN }.forEach { c -> FilterChip(selected = state.currency == c, onClick = { state.currency = c }, label = { Text(c.name) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = state.includeLiquidity, onClick = { state.includeLiquidity = !state.includeLiquidity }, label = { Text("ضمن السيولة") }); FilterChip(selected = state.includeNetWorth, onClick = { state.includeNetWorth = !state.includeNetWorth }, label = { Text("ضمن صافي الثروة") }) }
        PrimaryButton(label = "متابعة", enabled = state.displayName.isNotBlank() && runCatching { java.math.BigDecimal(state.openingBalance) }.isSuccess, onClick = onNext)
    }
}

@Composable
private fun PermissionStep(granted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit, onContinue: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("السماح بقراءة الرسائل البنكية", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text("يحتاج التطبيق إلى إذن قراءة الرسائل للتعرف على العمليات البنكية واستيرادها. التطبيق يقرأ الرسائل فقط، ولن يرسل أو يعدل أو يحذف أي رسالة.", style = MaterialTheme.typography.bodyLarge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = when {
                granted -> MaterialTheme.colorScheme.secondaryContainer
                permanentlyDenied -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                if (granted) "تم منح إذن قراءة الرسائل"
                else if (permanentlyDenied) "تم رفض الإذن. يمكنك منحه من إعدادات التطبيق"
                else "لم يتم منح إذن قراءة الرسائل",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (!granted) {
            PrimaryButton(label = "السماح بقراءة الرسائل", onClick = onRequest)
            SecondaryButton(label = "إعادة طلب الصلاحية", onClick = onRequest)
            if (permanentlyDenied) SecondaryButton(label = "فتح إعدادات التطبيق", onClick = onOpenSettings)
            return@Column
        }
        PrimaryButton(label = "متابعة", onClick = onContinue)
    }
}

@Composable
private fun CompletionStep(app: MasroofApplication, state: UiOnboardingState, onFinish: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(shape = FinancialShapes.pill, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp).size(36.dp),
            )
        }
        Text("جاهز للبدء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("تم إعداد التطبيق بنجاح. اضغط للبدء في استخدام مصروف.", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(label = "بدء استخدام التطبيق", onClick = onFinish)
    }
}

internal fun setupFrom(state: UiOnboardingState, completed: Boolean = false) = com.baraa.masroof.data.repository.FinancialSetup(
    trackingStartDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
    setupCompleted = completed,
    setupCompletedAt = if (completed) System.currentTimeMillis() else 0L,
    defaultCurrency = state.currency,
)

/**
 * Persist the account fields gathered during onboarding into Room.
 * Returns the auto-generated accountId, or null if validation / insert
 * failed. Called by [CompletionStep] BEFORE [OnboardingRepository.markCompleted]
 * so the onboarding completion flag is never saved without an account.
 */
private suspend fun persistOnboardingAccount(app: MasroofApplication, state: UiOnboardingState): Long? {
    // 1. Validate account name and type.
    if (state.displayName.isBlank()) return null
    val accountType = state.accountType
    // 2. Validate opening balance amount.
    val openingBalance = runCatching { java.math.BigDecimal(state.openingBalance) }.getOrNull() ?: return null
    if (openingBalance.signum() < 0) return null
    // 3. Validate opening balance date.
    val openingDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    // 4. Insert account via the repository.
    val accountId = app.financialAccountRepository.add(
        displayName = state.displayName.trim(),
        accountType = accountType,
        institutionName = state.institution.trim().takeIf { it.isNotBlank() },
        accountNature = com.baraa.masroof.transaction.AccountNature.defaultNatureFor(accountType),
        currency = state.currency,
        openingBalance = openingBalance,
        openingBalanceDate = openingDate,
        includeInNetWorth = state.includeNetWorth,
        includeInLiquidity = state.includeLiquidity,
    )
    if (accountId <= 0L) return null
    val lastFour = state.lastFour.takeIf { it.length == 4 && it.all(Char::isDigit) }
    if (lastFour != null) {
        val type = com.baraa.masroof.data.repository.AccountIdentifierRepository.defaultIdentifierTypeFor(accountType)
        if (type != null) {
            app.accountIdentifierRepository.addOrUpdate(
                accountId,
                com.baraa.masroof.data.repository.IdentifierForm(type, "معرف الحساب", lastFour),
            )
        }
    }
    // 5. Reload to confirm the row is actually persisted.
    val reloaded = app.financialAccountRepository.getById(accountId) ?: return null
    return reloaded.id
}
