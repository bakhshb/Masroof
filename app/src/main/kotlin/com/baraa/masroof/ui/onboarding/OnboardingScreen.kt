@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.repository.FinancialSetup
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Onboarding steps. PERMISSION is now the **first** gate that the user
 * cannot skip — without READ_SMS the rest of the app has no data to
 * import and balances can never update, so the onboarding state is
 * never marked `setupCompleted` while READ_SMS is missing.
 */
enum class OnboardingStep { PERMISSION, WELCOME, START_DATE, ACCOUNT, OPENING_BALANCE, COMPLETION }
enum class StartDateOption { TODAY, MONTH_START, CUSTOM }

class OnboardingState {
    var step by mutableStateOf(OnboardingStep.PERMISSION)
    var option by mutableStateOf(StartDateOption.TODAY)
    var trackingDate by mutableStateOf(LocalDate.now())
    var accountType by mutableStateOf(AccountType.BANK_ACCOUNT)
    var displayName by mutableStateOf("")
    var institution by mutableStateOf("")
    var lastFour by mutableStateOf("")
    var openingBalance by mutableStateOf("0")
    var currency by mutableStateOf(Currency.SAR)
    var includeLiquidity by mutableStateOf(true)
    var includeNetWorth by mutableStateOf(true)
    var skipped by mutableStateOf(false)
}

private fun isReadSmsGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val state = rememberSaveable(saver = OnboardingSaver) { OnboardingState() }
    var permissionGranted by remember { mutableStateOf(isReadSmsGranted(context)) }
    var permanentlyDenied by remember {
        mutableStateOf(
            !permissionGranted && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            permanentlyDenied = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
        } else {
            state.step = OnboardingStep.WELCOME
        }
    }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("إعداد مصروف") })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LinearProgressIndicator(progress = progress(state.step), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            when (state.step) {
                OnboardingStep.PERMISSION -> PermissionStep(granted = permissionGranted, permanentlyDenied = permanentlyDenied, onRequest = { launcher.launch(Manifest.permission.READ_SMS) }, onContinue = { state.step = OnboardingStep.WELCOME }, onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
                OnboardingStep.WELCOME -> WelcomeStep(onContinue = { state.step = OnboardingStep.START_DATE })
                OnboardingStep.START_DATE -> StartDateStep(state) { state.step = OnboardingStep.ACCOUNT }
                OnboardingStep.ACCOUNT -> AccountStep(state) { state.step = OnboardingStep.OPENING_BALANCE }
                OnboardingStep.OPENING_BALANCE -> OpeningBalanceStep(state) { state.step = OnboardingStep.COMPLETION }
                OnboardingStep.COMPLETION -> CompletionStep(app, state, readSmsGranted = permissionGranted, onFinish = {
                    scope.launch {
                        app.financialSetupRepository.save(setupFrom(state, completed = true))
                        onFinished()
                    }
                })
            }
        }
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

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("مرحبًا بك في مصروف", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("مصروف يساعدك على فهم أموالك تلقائيًا من رسائل البنك، دون الحاجة إلى إدخال كل عملية يدويًا.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateStep(state: OnboardingState, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("متى تبدأ المتابعة؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // Quick choices only — date selection is handled at import time
        // by the calendar-based date picker.
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
                            StartDateOption.TODAY -> LocalDate.now()
                            StartDateOption.MONTH_START -> YearMonth.now().atDay(1)
                            StartDateOption.CUSTOM -> state.trackingDate
                        }
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = state.option == opt, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        if (state.option == StartDateOption.CUSTOM) {
            // Read-only; opening the picker is done via the dedicated date
            // range UI inside the import flow (calendar picker, RTL-safe).
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
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة") }
    }
}

@Composable
private fun AccountStep(state: OnboardingState, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("أضف حسابك الأساسي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        val presets = listOf(AccountType.BANK_ACCOUNT to "حساب الراتب", AccountType.CREDIT_CARD to "بطاقة ائتمانية", AccountType.DIGITAL_WALLET to "محفظة رقمية", AccountType.CASH to "نقد")
        presets.forEach { (type, label) -> AssistChip(onClick = { state.accountType = type; state.displayName = label; state.includeLiquidity = AccountLiquidityDefaults.defaultFor(type); state.includeNetWorth = true }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) }
        Button(onClick = onNext, enabled = state.displayName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة") }
    }
}

@Composable
private fun OpeningBalanceStep(state: OnboardingState, onNext: () -> Unit) {
    val isLiability = AccountNature.defaultNatureFor(state.accountType) == AccountNature.LIABILITY
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("الرصيد الافتتاحي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(if (isLiability) "أدخل المبلغ المستحق عليك كرقم موجب." else "أدخل المبلغ الموجود في الحساب.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(state.displayName, { state.displayName = it }, label = { Text("اسم الحساب") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), isError = state.displayName.isBlank())
        OutlinedTextField(state.institution, { state.institution = it }, label = { Text("المؤسسة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.lastFour.take(4), { if (it.length <= 4 && it.all(Char::isDigit)) state.lastFour = it }, label = { Text("آخر 4 أرقام") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.openingBalance, { if (it.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) state.openingBalance = it }, label = { Text("الرصيد") }, modifier = Modifier.fillMaxWidth(), isError = runCatching { BigDecimal(state.openingBalance) }.isFailure)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Currency.values().filter { it != Currency.UNKNOWN }.forEach { c -> FilterChip(selected = state.currency == c, onClick = { state.currency = c }, label = { Text(c.name) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = state.includeLiquidity, onClick = { state.includeLiquidity = !state.includeLiquidity }, label = { Text("ضمن السيولة") }); FilterChip(selected = state.includeNetWorth, onClick = { state.includeNetWorth = !state.includeNetWorth }, label = { Text("ضمن صافي الثروة") }) }
        Button(onClick = onNext, enabled = state.displayName.isNotBlank() && runCatching { BigDecimal(state.openingBalance) }.isSuccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة") }
    }
}

@Composable
private fun PermissionStep(granted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit, onContinue: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text("السماح بقراءة الرسائل البنكية", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("السماح بقراءة الرسائل") }
            OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("إعادة طلب الصلاحية") }
            if (permanentlyDenied) TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("فتح إعدادات التطبيق") }
            // Cannot skip past the permission gate.
            return@Column
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("متابعة") }
    }
}

@Composable
private fun CompletionStep(app: MasroofApplication, state: OnboardingState, readSmsGranted: Boolean, onFinish: () -> Unit) {
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("جاهز للبدء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("تاريخ بدء المتابعة: ${state.trackingDate}")
            Text("عدد الحسابات: ${accounts.count { it.systemAccountKey == null }}")
            if (!readSmsGranted) Text("إذن قراءة الرسائل غير ممنوح؛ يمكنك إضافته لاحقًا من الإعدادات.", color = MaterialTheme.colorScheme.error)
        } }
        // Onboarding is only considered finished when both SMS permission
        // is granted and at least the basic setup is in place.
        Button(onClick = onFinish, enabled = readSmsGranted, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("الانتقال إلى الرئيسية") }
    }
}

internal fun setupFrom(state: OnboardingState, completed: Boolean = false) = FinancialSetup(trackingStartDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), setupCompleted = completed, setupCompletedAt = if (completed) System.currentTimeMillis() else 0L, defaultCurrency = state.currency)
