package com.baraa.masroof.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import com.baraa.masroof.onboarding.OnboardingImportPreview
import com.baraa.masroof.onboarding.OnboardingLinkPreview
import com.baraa.masroof.onboarding.PatternMatchCounts
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.time.LocalDate

enum class StartDateOption { TODAY, MONTH_START, CUSTOM }

class UiOnboardingState internal constructor(
    internal val restoredFromSaver: Boolean = false,
) {
    var step by mutableStateOf(OnboardingStep.WELCOME)
    var option by mutableStateOf(StartDateOption.TODAY)
    var trackingDate by mutableStateOf(LocalDate.now())
    var accountType by mutableStateOf(AccountType.BANK_ACCOUNT)
    var displayName by mutableStateOf("")
    var institution by mutableStateOf("")
    var patternSourceProfileId by mutableStateOf(0L)
    var patternSourceLabel by mutableStateOf("")
    var lastFour by mutableStateOf("")
    var identifierConfirmed by mutableStateOf(false)
    var openingBalance by mutableStateOf("0")
    var currency by mutableStateOf(Currency.SAR)
    var includeLiquidity by mutableStateOf(true)
    var includeNetWorth by mutableStateOf(true)
    var selectedSenderProfileId by mutableStateOf(0L)
    var selectedSenderKey by mutableStateOf("")
    var selectedSenderDisplay by mutableStateOf("")
    var selectedSmsBody by mutableStateOf<String?>(null)
    var draftTemplate by mutableStateOf("")
    var draftPlaceholders by mutableStateOf<List<String>>(emptyList())
    var draftSignature by mutableStateOf("")
    var draftFriendlyName by mutableStateOf("")
    var lastPatternCounts by mutableStateOf<PatternMatchCounts?>(null)
    var senderInbox by mutableStateOf<List<SmsMessage>>(emptyList())
    var importPreview by mutableStateOf<OnboardingImportPreview?>(null)
    var linkPreview by mutableStateOf<OnboardingLinkPreview?>(null)
    var createdAccountId by mutableStateOf(0L)
    var importStatus by mutableStateOf<String?>(null)
}

data class OnboardingDraft(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val option: StartDateOption = StartDateOption.TODAY,
    val trackingDate: LocalDate = LocalDate.now(),
    val accountType: AccountType = AccountType.BANK_ACCOUNT,
    val displayName: String = "",
    val institution: String = "",
    val patternSourceProfileId: Long = 0L,
    val patternSourceLabel: String = "",
    val lastFour: String = "",
    val identifierConfirmed: Boolean = false,
    val openingBalance: String = "0",
    val currency: Currency = Currency.SAR,
    val includeLiquidity: Boolean = true,
    val includeNetWorth: Boolean = true,
    val selectedSenderProfileId: Long = 0L,
    val selectedSenderKey: String = "",
    val selectedSenderDisplay: String = "",
    val createdAccountId: Long = 0L,
)

internal fun UiOnboardingState.toDraft() = OnboardingDraft(
    step = step,
    option = option,
    trackingDate = trackingDate,
    accountType = accountType,
    displayName = displayName,
    institution = institution,
    patternSourceProfileId = patternSourceProfileId,
    patternSourceLabel = patternSourceLabel,
    lastFour = lastFour,
    identifierConfirmed = identifierConfirmed,
    openingBalance = openingBalance,
    currency = currency,
    includeLiquidity = includeLiquidity,
    includeNetWorth = includeNetWorth,
    selectedSenderProfileId = selectedSenderProfileId,
    selectedSenderKey = selectedSenderKey,
    selectedSenderDisplay = selectedSenderDisplay,
    createdAccountId = createdAccountId,
)

internal fun UiOnboardingState.restoreDraft(draft: OnboardingDraft) {
    step = draft.step
    option = draft.option
    trackingDate = draft.trackingDate
    accountType = draft.accountType
    displayName = draft.displayName
    institution = draft.institution
    patternSourceProfileId = draft.patternSourceProfileId
    patternSourceLabel = draft.patternSourceLabel
    lastFour = draft.lastFour
    identifierConfirmed = draft.identifierConfirmed
    openingBalance = draft.openingBalance
    currency = draft.currency
    includeLiquidity = draft.includeLiquidity
    includeNetWorth = draft.includeNetWorth
    selectedSenderProfileId = draft.selectedSenderProfileId
    selectedSenderKey = draft.selectedSenderKey
    selectedSenderDisplay = draft.selectedSenderDisplay
    createdAccountId = draft.createdAccountId
}

val OnboardingSaver: Saver<UiOnboardingState, Any> = Saver(
    save = { state ->
        mapOf(
            "step" to state.step.name,
            "option" to state.option.name,
            "date" to state.trackingDate.toString(),
            "type" to state.accountType.name,
            "name" to state.displayName,
            "institution" to state.institution,
            "patternSourceProfileId" to state.patternSourceProfileId,
            "patternSourceLabel" to state.patternSourceLabel,
            "lastFour" to state.lastFour,
            "identifierConfirmed" to state.identifierConfirmed,
            "openingBalance" to state.openingBalance,
            "currency" to state.currency.name,
            "liquidity" to state.includeLiquidity,
            "netWorth" to state.includeNetWorth,
            "selectedSenderProfileId" to state.selectedSenderProfileId,
            "selectedSenderKey" to state.selectedSenderKey,
            "selectedSenderDisplay" to state.selectedSenderDisplay,
            "createdAccountId" to state.createdAccountId,
        )
    },
    restore = { map ->
        @Suppress("UNCHECKED_CAST")
        val m = map as Map<String, Any?>
        UiOnboardingState(restoredFromSaver = true).apply {
            step = mapPersistedStepName(m["step"] as? String) ?: OnboardingStep.WELCOME
            option = runCatching { StartDateOption.valueOf(m["option"] as String) }.getOrDefault(StartDateOption.TODAY)
            trackingDate = runCatching { LocalDate.parse(m["date"] as String) }.getOrDefault(LocalDate.now())
            accountType = runCatching { AccountType.valueOf(m["type"] as String) }.getOrDefault(AccountType.BANK_ACCOUNT)
            displayName = m["name"] as? String ?: ""
            institution = m["institution"] as? String ?: ""
            patternSourceProfileId = (m["patternSourceProfileId"] as? Number)?.toLong() ?: 0L
            patternSourceLabel = m["patternSourceLabel"] as? String ?: ""
            lastFour = m["lastFour"] as? String ?: ""
            identifierConfirmed = m["identifierConfirmed"] as? Boolean ?: false
            openingBalance = m["openingBalance"] as? String ?: "0"
            currency = runCatching { Currency.valueOf(m["currency"] as String) }.getOrDefault(Currency.SAR)
            includeLiquidity = m["liquidity"] as? Boolean ?: true
            includeNetWorth = m["netWorth"] as? Boolean ?: true
            selectedSenderProfileId = (m["selectedSenderProfileId"] as? Number)?.toLong() ?: 0L
            selectedSenderKey = m["selectedSenderKey"] as? String ?: ""
            selectedSenderDisplay = m["selectedSenderDisplay"] as? String ?: ""
            createdAccountId = (m["createdAccountId"] as? Number)?.toLong() ?: 0L
        }
    },
)
