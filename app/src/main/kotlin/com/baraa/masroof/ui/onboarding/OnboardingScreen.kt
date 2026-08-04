@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.baraa.masroof.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

enum class OnboardingStep { WELCOME, START_DATE, ACCOUNT, OPENING_BALANCE, PERMISSION, COMPLETION }
enum class StartDateOption { TODAY, MONTH_START, CUSTOM }

class OnboardingState {
    var step by mutableStateOf(OnboardingStep.WELCOME)
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
    var permissionDenied by mutableStateOf(false)
    var permissionPermanentlyDenied by mutableStateOf(false)
    var skipped by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val state = rememberSaveable(saver = OnboardingSaver) { OnboardingState() }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context as? Activity
        state.permissionDenied = !granted
        state.permissionPermanentlyDenied = !granted && (activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false)
        state.step = OnboardingStep.COMPLETION
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = { Text("إعداد مصروف") }, navigationIcon = {
            if (state.step != OnboardingStep.WELCOME) IconButton(onClick = { state.step = previousStep(state.step) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LinearProgressIndicator(progress = progress(state.step), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep { scope.launch { state.skipped = true; app.financialSetupRepository.save(setupFrom(state)); onFinished() } }
                OnboardingStep.START_DATE -> StartDateStep(state) { state.step = OnboardingStep.ACCOUNT }
                OnboardingStep.ACCOUNT -> AccountStep(state) { state.step = OnboardingStep.OPENING_BALANCE }
                OnboardingStep.OPENING_BALANCE -> OpeningBalanceStep(state) { state.step = OnboardingStep.PERMISSION }
                OnboardingStep.PERMISSION -> PermissionStep(state, launcher) { state.step = OnboardingStep.COMPLETION }
                OnboardingStep.COMPLETION -> CompletionStep(app, state) { scope.launch { app.financialSetupRepository.save(setupFrom(state, completed = true)); onFinished() } }
            }
        }
    }
}

private fun previousStep(step: OnboardingStep) = when (step) {
    OnboardingStep.COMPLETION -> OnboardingStep.PERMISSION; OnboardingStep.PERMISSION -> OnboardingStep.OPENING_BALANCE
    OnboardingStep.OPENING_BALANCE -> OnboardingStep.ACCOUNT; OnboardingStep.ACCOUNT -> OnboardingStep.START_DATE
    OnboardingStep.START_DATE -> OnboardingStep.WELCOME; else -> OnboardingStep.WELCOME
}

private fun progress(step: OnboardingStep) = when (step) { OnboardingStep.WELCOME -> 0.16f; OnboardingStep.START_DATE -> 0.32f; OnboardingStep.ACCOUNT -> 0.48f; OnboardingStep.OPENING_BALANCE -> 0.64f; OnboardingStep.PERMISSION -> 0.82f; OnboardingStep.COMPLETION -> 1f }

@Composable
private fun WelcomeStep(onSkip: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("مرحبًا بك في مصروف", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("مصروف يساعدك على فهم أموالك تلقائيًا من رسائل البنك، دون الحاجة إلى إدخال كل عملية يدويًا.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { /* state is owned by the caller via step transition */ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("ابدأ الإعداد") }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("استكشاف التطبيق أولًا") }
    }
}

@Composable
private fun StartDateStep(state: OnboardingState, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("متى تبدأ المتابعة؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow {
            StartDateOption.values().forEachIndexed { idx, opt -> SegmentedButton(selected = state.option == opt, onClick = { state.option = opt; state.trackingDate = when (opt) { StartDateOption.TODAY -> LocalDate.now(); StartDateOption.MONTH_START -> YearMonth.now().atDay(1); StartDateOption.CUSTOM -> state.trackingDate } }, shape = SegmentedButtonDefaults.itemShape(idx, StartDateOption.values().size)) { Text(opt.label()) } }
        }
        if (state.option == StartDateOption.CUSTOM) {
            OutlinedTextField(value = state.trackingDate.toString(), onValueChange = { runCatching { state.trackingDate = LocalDate.parse(it) } }, label = { Text("تاريخ مخصص") }, modifier = Modifier.fillMaxWidth())
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
        OutlinedButton(onClick = { state.step = OnboardingStep.OPENING_BALANCE }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("إضافة حساب آخر") }
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
private fun PermissionStep(state: OnboardingState, launcher: androidx.activity.result.ActivityResultLauncher<String>, onSkip: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("قراءة رسائل البنك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        listOf("يُستخدم الإذن لقراءة رسائل البنك والتعرف على العمليات تلقائيًا.", "تبقى الرسائل على جهازك فقط ولا يتم إرسالها لأي جهة.", "التطبيق لا يستطيع إرسال رسائل أو حذفها.", "العمليات المستوردة تُعرض للمراجعة قبل أن تؤثر على الأرصدة.").forEach { Text("• $it") }
        Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("السماح بقراءة الرسائل") }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("تخطي الآن") }
        if (state.permissionPermanentlyDenied) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) { Text("افتح إعدادات التطبيق") }
    }
}

@Composable
private fun CompletionStep(app: MasroofApplication, state: OnboardingState, onFinish: () -> Unit) {
    val accounts by app.financialAccountRepository.observeAll().collectAsStateWithLifecycle(emptyList())
    val liquidity = accounts.filter { it.includeInLiquidity && it.systemAccountKey == null }.fold(BigDecimal.ZERO) { a, acc -> a + acc.openingBalance }
    val netWorth = accounts.filter { it.includeInNetWorth && it.systemAccountKey == null && AccountNature.defaultNatureFor(it.accountType) == AccountNature.ASSET }.fold(BigDecimal.ZERO) { a, acc -> a + acc.openingBalance } - accounts.filter { it.includeInNetWorth && it.systemAccountKey == null && AccountNature.defaultNatureFor(it.accountType) == AccountNature.LIABILITY }.fold(BigDecimal.ZERO) { a, acc -> a + acc.openingBalance }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("جاهز للبدء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("تاريخ بدء المتابعة: ${state.trackingDate}")
            Text("عدد الحسابات: ${accounts.count { it.systemAccountKey == null }}")
            Text("السيولة الافتتاحية: $liquidity ${state.currency.name}")
            Text("صافي الثروة الافتتاحي: $netWorth ${state.currency.name}")
            if (state.permissionDenied) Text("إذن قراءة الرسائل غير ممنوح؛ يمكنك إضافته لاحقًا من الإعدادات.")
        } }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("الانتقال إلى الرئيسية") }
    }
}

private fun StartDateOption.label() = when (this) { StartDateOption.TODAY -> "من اليوم"; StartDateOption.MONTH_START -> "من بداية هذا الشهر"; StartDateOption.CUSTOM -> "اختيار تاريخ سابق" }

private fun setupFrom(state: OnboardingState, completed: Boolean = false) = FinancialSetup(trackingStartDate = state.trackingDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), setupCompleted = completed, setupCompletedAt = if (completed) System.currentTimeMillis() else 0L, defaultCurrency = state.currency)