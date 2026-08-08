package com.baraa.masroof.ui.onboarding

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.FinancialAccountRepository
import com.baraa.masroof.data.repository.IdentifierAddResult
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.ledger.AccountIdentifierCompatibility
import com.baraa.masroof.transaction.AccountNature
import java.math.BigDecimal
import java.time.ZoneId

internal data class SelectedSender(
    val id: Long,
    val normalizedKey: String,
    val displayName: String,
)

internal suspend fun createOrUpdateOnboardingAccount(
    state: UiOnboardingState,
    repository: OnboardingRepository,
    financialAccountRepository: FinancialAccountRepository,
    accountIdentifierRepository: AccountIdentifierRepository,
): Long = createOrUpdateOnboardingAccount(
    state = state,
    repository = repository,
    financialAccountRepository = financialAccountRepository,
    saveOptionalIdentifier = { accountId, accountType, lastFour ->
        if (lastFour.isNotBlank()) {
            val identifierType = AccountIdentifierRepository.defaultIdentifierTypeFor(accountType)
                ?: AccountIdentifierType.ACCOUNT_LAST4
            require(
                AccountIdentifierCompatibility.isCompatibleTyped(accountType, identifierType),
            ) {
                "نوع المعرف غير متوافق مع نوع الحساب"
            }
            val normalized = AccountIdentifierRepository.normalize(identifierType, lastFour)
            require(
                AccountIdentifierRepository.validate(identifierType, normalized) == null,
            ) {
                "يجب أن يحتوي المعرف على أربعة أرقام بالضبط"
            }
            val sameType = accountIdentifierRepository.getActiveForAccount(accountId)
                .filter { it.identifierType == identifierType }
            val exact = sameType.firstOrNull { it.normalizedValue == normalized }
            val outcome = accountIdentifierRepository.addOrUpdate(
                accountId,
                IdentifierForm(identifierType, "معرف الحساب", lastFour),
            )
            check(outcome.result != IdentifierAddResult.Rejected) {
                outcome.message ?: "تعذر حفظ معرف الحساب"
            }
            if (exact == null) {
                sameType.forEach { previous ->
                    accountIdentifierRepository.setActive(previous.id, false)
                }
            }
        }
    },
)

internal suspend fun createOrUpdateOnboardingAccount(
    state: UiOnboardingState,
    repository: OnboardingRepository,
    financialAccountRepository: FinancialAccountRepository,
    saveOptionalIdentifier: suspend (
        accountId: Long,
        accountType: com.baraa.masroof.transaction.AccountType,
        lastFour: String,
    ) -> Unit,
): Long {
    val balance = runCatching { BigDecimal(state.openingBalance) }.getOrNull()
    require(balance != null && balance.signum() >= 0) { "رصيد غير صالح" }
    require(state.displayName.isNotBlank()) { "اسم الحساب مطلوب" }
    require(state.lastFour.isBlank() || state.lastFour.matches(Regex("""\d{4}"""))) {
        "يجب أن يحتوي المعرف على أربعة أرقام بالضبط"
    }
    val openingDate = state.trackingDate
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val existingId = state.createdAccountId
    val existing = existingId.takeIf { it > 0L }
        ?.let { financialAccountRepository.getById(it) }
    val accountId = if (existing != null) {
        financialAccountRepository.update(
            existing.copy(
                displayName = state.displayName.trim(),
                institutionName = state.institution.trim().takeIf(String::isNotBlank),
                accountType = state.accountType,
                accountNature = AccountNature.defaultNatureFor(state.accountType),
                currency = state.currency,
                openingBalance = balance,
                openingBalanceDate = openingDate,
                includeInNetWorth = state.includeNetWorth,
                includeInLiquidity = state.includeLiquidity,
            ),
        )
        existing.id
    } else {
        state.createdAccountId = 0L
        val createdId = financialAccountRepository.add(
            displayName = state.displayName.trim(),
            accountType = state.accountType,
            institutionName = state.institution.trim().takeIf(String::isNotBlank),
            accountNature = AccountNature.defaultNatureFor(state.accountType),
            currency = state.currency,
            openingBalance = balance,
            openingBalanceDate = openingDate,
            includeInNetWorth = state.includeNetWorth,
            includeInLiquidity = state.includeLiquidity,
        )
        require(createdId > 0L) { "تعذر حفظ الحساب" }
        state.createdAccountId = createdId
        repository.saveDraft(state.toDraft())
        createdId
    }
    state.createdAccountId = accountId
    repository.saveDraft(state.toDraft())
    saveOptionalIdentifier(accountId, state.accountType, state.lastFour)
    state.identifierConfirmed = state.lastFour.length == 4
    repository.saveDraft(state.toDraft())
    return accountId
}

internal suspend fun associateSelectedSender(
    state: UiOnboardingState,
    rawSender: String,
    upsertSender: suspend (String) -> SelectedSender,
    associateAccount: suspend (Long, Long) -> Unit,
) {
    require(state.createdAccountId > 0L) { "account required before sender" }
    val sender = upsertSender(rawSender)
    associateAccount(state.createdAccountId, sender.id)
    state.selectedSenderProfileId = sender.id
    state.selectedSenderKey = sender.normalizedKey
    state.selectedSenderDisplay = sender.displayName
}

internal suspend fun completeMinimalOnboarding(
    accountId: Long,
    accountExists: suspend (Long) -> Boolean,
    saveFinancialSetup: suspend () -> Unit,
    markCompleted: suspend () -> Unit,
) {
    require(accountId > 0L && accountExists(accountId)) {
        "account required before onboarding completion"
    }
    saveFinancialSetup()
    markCompleted()
}
