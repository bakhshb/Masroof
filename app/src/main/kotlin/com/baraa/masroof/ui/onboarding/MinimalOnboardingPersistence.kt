package com.baraa.masroof.ui.onboarding

internal data class SelectedSender(
    val id: Long,
    val normalizedKey: String,
    val displayName: String,
)

internal suspend fun persistAccountOnce(
    state: UiOnboardingState,
    repository: OnboardingRepository,
    accountExists: suspend (Long) -> Boolean,
    createAccount: suspend () -> Long,
    saveOptionalIdentifier: suspend (Long) -> Unit,
): Long {
    val existingId = state.createdAccountId
    if (existingId > 0L && accountExists(existingId)) return existingId

    val createdId = createAccount()
    require(createdId > 0L) { "account insert failed" }
    state.createdAccountId = createdId
    repository.saveDraft(state.toDraft())
    saveOptionalIdentifier(createdId)
    repository.saveDraft(state.toDraft())
    return createdId
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
