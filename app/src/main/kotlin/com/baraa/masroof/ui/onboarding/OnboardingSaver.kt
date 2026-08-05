package com.baraa.masroof.ui.onboarding

import androidx.compose.runtime.saveable.Saver
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import java.time.LocalDate

val OnboardingSaver: Saver<OnboardingState, Any> = Saver(
    save = { state ->
        mapOf(
            "step" to state.step.name, "option" to state.option.name, "date" to state.trackingDate.toString(),
            "type" to state.accountType.name, "name" to state.displayName, "institution" to state.institution,
            "lastFour" to state.lastFour, "openingBalance" to state.openingBalance, "currency" to state.currency.name,
            "liquidity" to state.includeLiquidity, "netWorth" to state.includeNetWorth,
            "skipped" to state.skipped,
        )
    },
    restore = { map ->
        @Suppress("UNCHECKED_CAST")
        val m = map as Map<String, Any?>
        OnboardingState().apply {
            step = runCatching { OnboardingStep.valueOf(m["step"] as String) }.getOrDefault(OnboardingStep.PERMISSION)
            option = runCatching { StartDateOption.valueOf(m["option"] as String) }.getOrDefault(StartDateOption.TODAY)
            trackingDate = runCatching { LocalDate.parse(m["date"] as String) }.getOrDefault(LocalDate.now())
            accountType = runCatching { AccountType.valueOf(m["type"] as String) }.getOrDefault(AccountType.BANK_ACCOUNT)
            displayName = m["name"] as? String ?: ""
            institution = m["institution"] as? String ?: ""
            lastFour = m["lastFour"] as? String ?: ""
            openingBalance = m["openingBalance"] as? String ?: "0"
            currency = runCatching { Currency.valueOf(m["currency"] as String) }.getOrDefault(Currency.SAR)
            includeLiquidity = m["liquidity"] as? Boolean ?: true
            includeNetWorth = m["netWorth"] as? Boolean ?: true
            skipped = m["skipped"] as? Boolean ?: false
        }
    },
)
