package com.baraa.masroof.application.dashboard

import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.AccountRegistryEntry
import com.baraa.masroof.domain.model.Bank

internal data class OwnedAccountScope(
    val bank: Bank,
    val maskedNumber: String,
    val containerId: String,
    val scope: CurrentAccountTransactionScope,
)

internal object CurrentAccountScopeFactory {
    fun singleAccountScopes(
        ownedAccounts: List<AccountRegistryEntry>,
    ): List<OwnedAccountScope> =
        ownedAccounts.mapNotNull { account ->
            val containerId = FinancialContainerIdFactory.accountId(account.bank, account.maskedNumber)
                ?: return@mapNotNull null
            val last4s = CurrentAccountTransactionScope.ownedAccountLast4sFromMaskedNumbers(
                listOf(account.maskedNumber),
            )
            OwnedAccountScope(
                bank = account.bank,
                maskedNumber = account.maskedNumber,
                containerId = containerId,
                scope = CurrentAccountTransactionScope(
                    ownedContainerIds = setOf(containerId),
                    ownedAccountLast4s = last4s,
                    mode = AccountFlowScopeMode.SingleAccount,
                ),
            )
        }
}
