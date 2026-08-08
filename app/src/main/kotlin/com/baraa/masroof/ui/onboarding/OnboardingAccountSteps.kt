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
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.data.repository.SenderProfile
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
fun AccountFromPatternsStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<SenderProfile>>(emptyList()) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val approvedIds = app.messagePatternRepository.senderProfileIdsWithApprovedPatterns()
        sources = app.senderProfileRepository.getActive().filter { it.id in approvedIds }
        if (state.patternSourceProfileId <= 0L && sources.isNotEmpty()) {
            val first = sources.first()
            state.patternSourceProfileId = first.id
            state.patternSourceLabel = first.displayInstitutionName ?: first.displaySender
            state.institution = state.patternSourceLabel
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إنشاء حساب", style = MaterialTheme.typography.titleLarge)
        Text(
            "مصدر الأنماط يجب أن يكون مرسلاً له أنماط معتمدة — وليس اختيار مرسل خام من الصندوق.",
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
        ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = !sourceExpanded }) {
            OutlinedTextField(
                value = state.patternSourceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("البنك / مصدر الأنماط") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(sourceExpanded, { sourceExpanded = false }) {
                sources.forEach { profile ->
                    val label = profile.displayInstitutionName ?: profile.displaySender
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            state.patternSourceProfileId = profile.id
                            state.patternSourceLabel = label
                            state.institution = label
                            sourceExpanded = false
                        },
                    )
                }
            }
        }
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            if (state.createdAccountId > 0L) "متابعة إلى المعرفات" else "حفظ الحساب والمتابعة",
            enabled = state.displayName.isNotBlank() &&
                state.patternSourceProfileId > 0L &&
                runCatching { BigDecimal(state.openingBalance) }.isSuccess,
            onClick = {
                if (state.createdAccountId > 0L) {
                    onContinue()
                    return@PrimaryButton
                }
                scope.launch {
                    val balance = runCatching { BigDecimal(state.openingBalance) }.getOrNull()
                    if (balance == null || balance.signum() < 0) {
                        error = "رصيد غير صالح"
                        return@launch
                    }
                    val openingDate = state.trackingDate
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    val id = app.financialAccountRepository.add(
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
                    if (id <= 0L) {
                        error = "تعذر حفظ الحساب"
                        return@launch
                    }
                    app.senderProfileRepository.associateAccount(id, state.patternSourceProfileId)
                    app.financialSetupRepository.save(setupFrom(state, completed = false))
                    state.createdAccountId = id
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun IdentifiersStep(
    app: MasroofApplication,
    state: UiOnboardingState,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var suggested by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val defaultType = remember(state.accountType) {
        com.baraa.masroof.data.repository.AccountIdentifierRepository.defaultIdentifierTypeFor(state.accountType)
            ?: AccountIdentifierType.ACCOUNT_LAST4
    }
    LaunchedEffect(state.selectedSenderProfileId) {
        val patterns = app.messagePatternRepository.getForSender(state.selectedSenderProfileId)
            .filter { it.definition.status == MessagePatternStatus.APPROVED }
        val fromTemplates = patterns.flatMap { p ->
            val t = p.definition.templateText.orEmpty()
            listOfNotNull(
                if ("CREDIT_CARD_LAST4" in t) "بطاقة ائتمان" else null,
                if ("ACCOUNT_LAST4" in t) "حساب" else null,
                if ("DEBIT_CARD_LAST4" in t) "مدى" else null,
            )
        }.distinct()
        suggested = fromTemplates
        // Suggest digits from a sample SMS if present — user must still confirm typing.
        val sample = state.selectedSmsBody
        if (sample != null) {
            val digits = Regex("""(?<!\d)(\d{4})(?!\d)""").findAll(sample).map { it.groupValues[1] }.toList()
            if (digits.isNotEmpty() && state.lastFour.isBlank()) {
                // Do not auto-fill; only show as hint text below.
                suggested = suggested + digits.map { "مقترح من رسالة: $it (أدخله يدوياً للتأكيد)" }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("معرفات الحساب", style = MaterialTheme.typography.titleLarge)
        Text(
            "أدخل آخر 4 أرقام يدوياً. الأرقام المكتشفة في الرسائل تُعرض كاقتراح فقط ولا تُحفظ تلقائياً.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("النوع المتوقع: ${typeLabel(defaultType)}", style = MaterialTheme.typography.bodySmall)
        suggested.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(
            value = state.lastFour,
            onValueChange = {
                if (it.length <= 4 && it.all(Char::isDigit)) {
                    state.lastFour = it
                    state.identifierConfirmed = false
                }
            },
            label = { Text("آخر 4 أرقام") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(
            "تأكيد المعرف والمتابعة",
            enabled = state.lastFour.length == 4 && state.createdAccountId > 0L,
            onClick = {
                scope.launch {
                    if (!AccountIdentifierCompatibility.isCompatibleTyped(state.accountType, defaultType)) {
                        error = "نوع المعرف غير متوافق مع نوع الحساب"
                        return@launch
                    }
                    val outcome = app.accountIdentifierRepository.addOrUpdate(
                        state.createdAccountId,
                        IdentifierForm(defaultType, "معرف الحساب", state.lastFour),
                    )
                    if (outcome.result == com.baraa.masroof.data.repository.IdentifierAddResult.Rejected) {
                        error = outcome.message ?: "تعذر حفظ المعرف"
                        return@launch
                    }
                    state.identifierConfirmed = true
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            "تخطّي الآن (ستحتاج مراجعة لاحقاً)",
            onClick = onContinue,
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

private fun typeLabel(type: AccountIdentifierType): String = when (type) {
    AccountIdentifierType.CREDIT_CARD_LAST4 -> "CREDIT_CARD_LAST4"
    AccountIdentifierType.DEBIT_CARD_LAST4 -> "DEBIT_CARD_LAST4"
    AccountIdentifierType.IBAN_LAST4 -> "IBAN_LAST4"
    AccountIdentifierType.WALLET_LAST4 -> "WALLET_LAST4"
    AccountIdentifierType.ACCOUNT_LAST4 -> "ACCOUNT_LAST4"
}
