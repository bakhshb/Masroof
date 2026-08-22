package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountRegistryEntry

internal object CurrentAccountScopeFactory {
    fun singleAccountScopes(
        ownedAccounts: List<AccountRegistryEntry>,
    ): Map<String, CurrentAccountTransactionScope> =
        ownedAccounts.mapNotNull { account ->
            val containerId = FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
                ?: return@mapNotNull null
            val last4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
                listOf(account.maskedNumber),
            )
            containerId to CurrentAccountTransactionScope(
                ownedContainerIds = setOf(containerId),
                ownedAccountLast4s = last4s,
                mode = AccountFlowScopeMode.SingleAccount,
            )
        }.toMap()
}
