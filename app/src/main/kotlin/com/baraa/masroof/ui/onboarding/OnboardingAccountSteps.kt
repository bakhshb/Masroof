package com.baraa.masroof.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.ledger.AccountIdentifierCompatibility
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.ui.theme.CalendarDateField
import com.baraa.masroof.ui.theme.PrimaryButton
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    repository: OnboardingRepository,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val identifierType = remember(state.accountType) {
        com.baraa.masroof.data.repository.AccountIdentifierRepository
            .defaultIdentifierTypeFor(state.accountType)
            ?: AccountIdentifierType.ACCOUNT_LAST4
    }
    LaunchedEffect(state.createdAccountId) {
        if (state.createdAccountId > 0L) {
            val existing = app.financialAccountRepository.getById(state.createdAccountId)
            if (existing == null) {
                state.createdAccountId = 0L
            } else {
                state.displayName = existing.displayName
                state.accountType = existing.accountType
                state.institution = existing.institutionName.orEmpty()
                state.currency = existing.currency
                state.openingBalance = existing.openingBalance.toPlainString()
                state.includeNetWorth = existing.includeInNetWorth
                state.includeLiquidity = existing.includeInLiquidity
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إنشاء حساب", style = MaterialTheme.typography.titleLarge)
        Text(
            "أنشئ حسابك أولاً. يمكنك ربط مرسل الرسائل في الخطوة التالية دون الحاجة إلى نمط معتمد.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = { state.displayName = it },
            label = { Text("اسم الحساب") },
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
            OutlinedTextField(
                value = accountTypeLabel(state.accountType),
                onValueChange = {},
                readOnly = true,
                label = { Text("نوع الحساب") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                AccountType.setupTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(accountTypeLabel(type)) },
                        onClick = {
                            state.accountType = type
                            state.includeLiquidity = AccountLiquidityDefaults.defaultFor(type)
                            typeExpanded = false
                        },
                    )
                }
            }
        }
        Text("العملة", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Currency.SAR, Currency.USD, Currency.EUR).forEach { currency ->
                FilterChip(
                    selected = state.currency == currency,
                    onClick = { state.currency = currency },
                    label = { Text(currency.name) },
                )
            }
        }
        OutlinedTextField(
            value = state.institution,
            onValueChange = { state.institution = it },
            label = { Text("اسم البنك / المؤسسة (اختياري)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("تاريخ بداية المتابعة والرصيد الافتتاحي", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.option == StartDateOption.TODAY,
                onClick = {
                    state.option = StartDateOption.TODAY
                    state.trackingDate = LocalDate.now()
                },
                label = { Text("اليوم") },
            )
            FilterChip(
                selected = state.option == StartDateOption.MONTH_START,
                onClick = {
                    state.option = StartDateOption.MONTH_START
                    state.trackingDate = LocalDate.now().withDayOfMonth(1)
                },
                label = { Text("بداية الشهر") },
            )
            FilterChip(
                selected = state.option == StartDateOption.CUSTOM,
                onClick = { state.option = StartDateOption.CUSTOM },
                label = { Text("مخصص") },
            )
        }
        if (state.option == StartDateOption.CUSTOM) {
            CalendarDateField(
                label = "تاريخ بداية المتابعة",
                selected = state.trackingDate,
                onSelected = { state.trackingDate = it },
                maxDate = LocalDate.now(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.openingBalance,
            onValueChange = { if (it.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) state.openingBalance = it },
            label = { Text("الرصيد الافتتاحي") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.lastFour,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) state.lastFour = it
            },
            label = { Text("آخر 4 أرقام (اختياري)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            when {
                saving -> "جارٍ الحفظ…"
                state.createdAccountId > 0L -> "متابعة"
                else -> "حفظ الحساب والمتابعة"
            },
            enabled = state.displayName.isNotBlank() &&
                runCatching { BigDecimal(state.openingBalance) }.isSuccess &&
                !saving,
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    val balance = runCatching { BigDecimal(state.openingBalance) }.getOrNull()
                    if (balance == null || balance.signum() < 0) {
                        error = "رصيد غير صالح"
                        saving = false
                        return@launch
                    }
                    val openingDate = state.trackingDate
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    if (
                        state.lastFour.length == 4 &&
                        !AccountIdentifierCompatibility.isCompatibleTyped(state.accountType, identifierType)
                    ) {
                        error = "نوع المعرف غير متوافق مع نوع الحساب"
                        saving = false
                        return@launch
                    }
                    runCatching {
                        persistAccountOnce(
                            state = state,
                            repository = repository,
                            accountExists = { id ->
                                app.financialAccountRepository.getById(id) != null
                            },
                            createAccount = {
                                app.financialAccountRepository.add(
                                    displayName = state.displayName.trim(),
                                    accountType = state.accountType,
                                    institutionName = state.institution.trim().takeIf { it.isNotBlank() },
                                    accountNature = AccountNature.defaultNatureFor(state.accountType),
                                    currency = state.currency,
                                    openingBalance = balance,
                                    openingBalanceDate = openingDate,
                                    includeInNetWorth = state.includeNetWorth,
                                    includeInLiquidity = state.includeLiquidity,
                                )
                            },
                            saveOptionalIdentifier = { id ->
                                if (state.lastFour.length == 4) {
                                    val outcome = app.accountIdentifierRepository.addOrUpdate(
                                        id,
                                        IdentifierForm(identifierType, "معرف الحساب", state.lastFour),
                                    )
                                    check(
                                        outcome.result !=
                                            com.baraa.masroof.data.repository.IdentifierAddResult.Rejected,
                                    ) {
                                        outcome.message ?: "identifier rejected"
                                    }
                                    state.identifierConfirmed = true
                                }
                            },
                        )
                    }.onSuccess {
                        onContinue()
                    }.onFailure {
                        error = if (state.createdAccountId > 0L) {
                            "تم حفظ الحساب، لكن تعذر حفظ المعرف الاختياري"
                        } else {
                            "تعذر حفظ الحساب"
                        }
                    }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.BANK_ACCOUNT -> "حساب بنكي"
    AccountType.CREDIT_CARD -> "بطاقة ائتمانية"
    AccountType.DIGITAL_WALLET -> "محفظة رقمية"
    AccountType.CASH -> "نقد"
    else -> type.name
}
