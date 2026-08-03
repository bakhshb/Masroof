package com.baraa.masroof.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.accounts.OpeningBalanceCalculator
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.repository.FinancialSetup
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** Four-step, resumable Arabic setup for accounts and opening balances. */
private enum class SetupStep { START_DATE, ACCOUNTS, OPENING_BALANCES, SUMMARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupFlowScreen(
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MasroofApplication
    val accountsRepository = app.financialAccountRepository
    val setupRepository = app.financialSetupRepository
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SetupStep.START_DATE) }
    var trackingDate by remember { mutableStateOf(LocalDate.now()) }
    var defaultCurrency by remember { mutableStateOf(Currency.SAR) }
    var accounts by remember { mutableStateOf<List<FinancialAccount>>(emptyList()) }
    var editing by remember { mutableStateOf<FinancialAccount?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val setup = setupRepository.load()
        trackingDate = setup.trackingStartDate.toLocalDateOrToday()
        defaultCurrency = setup.defaultCurrency
    }
    LaunchedEffect(accountsRepository) {
        accountsRepository.observeAll().collectLatest { accounts = it }
    }

    fun saveSetup(completed: Boolean) {
        scope.launch {
            setupRepository.save(
                FinancialSetup(
                    trackingStartDate = trackingDate.toEpochMillis(),
                    setupCompleted = completed,
                    setupCompletedAt = if (completed) System.currentTimeMillis() else 0L,
                    defaultCurrency = defaultCurrency,
                ),
            )
            if (completed) onFinished() else onClose()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("إعداد الحسابات المالية") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepIndicator(step)
            when (step) {
                SetupStep.START_DATE -> StartDateStep(
                    trackingDate = trackingDate,
                    onDateChanged = { trackingDate = it },
                    onSkip = { saveSetup(false) },
                    onNext = { step = SetupStep.ACCOUNTS },
                )
                SetupStep.ACCOUNTS -> AccountsStep(
                    accounts = accounts,
                    onAdd = { adding = true },
                    onEdit = { editing = it },
                    onSkip = { saveSetup(false) },
                    onNext = { step = SetupStep.OPENING_BALANCES },
                )
                SetupStep.OPENING_BALANCES -> OpeningBalancesStep(
                    accounts = accounts,
                    onEdit = { editing = it },
                    onSkip = { saveSetup(false) },
                    onNext = { step = SetupStep.SUMMARY },
                )
                SetupStep.SUMMARY -> SummaryStep(
                    accounts = accounts,
                    trackingDate = trackingDate,
                    onEditAccounts = { step = SetupStep.ACCOUNTS },
                    onFinish = { saveSetup(true) },
                )
            }
        }
    }

    if (adding) {
        AccountEditDialog(
            existing = null,
            existingAccounts = accounts,
            defaultOpeningDate = trackingDate,
            onDismiss = { adding = false },
            onSave = { draft ->
                scope.launch {
                    accountsRepository.add(
                        displayName = draft.displayName, accountType = draft.accountType,
                        institutionName = draft.institutionName, lastFourDigits = draft.lastFourDigits,
                        senderAliases = draft.senderAliases, accountNature = draft.accountNature,
                        currency = draft.currency, openingBalance = draft.openingBalance,
                        openingBalanceDate = draft.openingBalanceDate,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInLiquidity = draft.includeInLiquidity, notes = draft.notes,
                    )
                    adding = false
                }
            },
        )
    }
    editing?.let { account ->
        AccountEditDialog(
            existing = account,
            existingAccounts = accounts,
            onDismiss = { editing = null },
            onSave = { draft ->
                scope.launch {
                    accountsRepository.update(account.copy(
                        displayName = draft.displayName, institutionName = draft.institutionName,
                        accountType = draft.accountType, accountNature = draft.accountNature,
                        lastFourDigits = draft.lastFourDigits, senderAliases = draft.senderAliases,
                        currency = draft.currency, openingBalance = draft.openingBalance,
                        openingBalanceDate = draft.openingBalanceDate,
                        includeInNetWorth = draft.includeInNetWorth,
                        includeInLiquidity = draft.includeInLiquidity, isActive = draft.isActive,
                        notes = draft.notes,
                    ))
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun StepIndicator(step: SetupStep) {
    val label = when (step) {
        SetupStep.START_DATE -> "1 / 4"
        SetupStep.ACCOUNTS -> "2 / 4"
        SetupStep.OPENING_BALANCES -> "3 / 4"
        SetupStep.SUMMARY -> "4 / 4"
    }
    Text(label, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun StartDateStep(
    trackingDate: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    var dateText by remember(trackingDate) { mutableStateOf(trackingDate.toString()) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    Text("تاريخ بدء المتابعة", style = MaterialTheme.typography.headlineSmall)
    Text("اختر التاريخ الذي تريد أن يبدأ منه التطبيق في متابعة بياناتك المالية.")
    androidx.compose.material3.OutlinedTextField(
        value = dateText, onValueChange = { dateText = it },
        label = { Text("التاريخ (YYYY-MM-DD)") }, isError = date == null || date.isAfter(LocalDate.now()),
        modifier = Modifier.fillMaxWidth(),
    )
    FlowActions(onSkip, enabled = date != null && !date.isAfter(LocalDate.now())) {
        onDateChanged(date!!); onNext()
    }
}

@Composable
private fun AccountsStep(
    accounts: List<FinancialAccount>,
    onAdd: () -> Unit,
    onEdit: (FinancialAccount) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Text("إضافة حساباتك", style = MaterialTheme.typography.headlineSmall)
    Text("أضف حساباتك يدويًا. يمكنك إضافة المزيد أو تعديلها لاحقًا.")
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("إضافة حساب")
    }
    AccountCards(accounts, onEdit)
    FlowActions(onSkip, enabled = true, onNext)
}

@Composable
private fun OpeningBalancesStep(
    accounts: List<FinancialAccount>,
    onEdit: (FinancialAccount) -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Text("إدخال الأرصدة الافتتاحية", style = MaterialTheme.typography.headlineSmall)
    Text("اضغط على أي حساب لإدخال أو تعديل رصيده الافتتاحي. تُدخل الالتزامات كمبالغ موجبة.")
    AccountCards(accounts, onEdit)
    FlowActions(onSkip, enabled = true, onNext)
}

@Composable
private fun SummaryStep(
    accounts: List<FinancialAccount>,
    trackingDate: LocalDate,
    onEditAccounts: () -> Unit,
    onFinish: () -> Unit,
) {
    val totals = OpeningBalanceCalculator.compute(accounts)
    Text("مراجعة الملخص", style = MaterialTheme.typography.headlineSmall)
    Text("تاريخ بدء المتابعة: $trackingDate")
    Text("عدد الحسابات: ${accounts.size}")
    if (totals.perCurrency.isEmpty()) Text("لم تُضف حسابات بعد.")
    totals.perCurrency.values.forEach { total ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(total.currency.name, style = MaterialTheme.typography.titleMedium)
                Text("إجمالي الأصول: ${total.assets}")
                Text("إجمالي الالتزامات: ${total.liabilities}")
                Text("السيولة الافتتاحية: ${total.liquidity}")
                Text("صافي الثروة الافتتاحي: ${total.netWorth}")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onEditAccounts, modifier = Modifier.weight(1f)) { Text("تعديل") }
        Button(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("استكمال الإعداد") }
    }
}

@Composable
private fun AccountCards(accounts: List<FinancialAccount>, onEdit: (FinancialAccount) -> Unit) {
    accounts.forEach { account ->
        Card(
            onClick = { onEdit(account) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                Text("${accountTypeLabel(account.accountType)} • ${account.openingBalance.toPlainString()} ${account.currency.name}")
            }
        }
    }
}

@Composable
private fun FlowActions(onSkip: () -> Unit, enabled: Boolean, onNext: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("تخطي الآن") }
        Button(onClick = onNext, enabled = enabled, modifier = Modifier.weight(1f)) { Text("التالي") }
    }
}

private fun Long.toLocalDateOrToday(): LocalDate =
    if (this <= 0L) LocalDate.now() else java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
