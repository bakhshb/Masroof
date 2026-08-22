package com.baraa.masroof.presentation.dashboard

import com.baraa.masroof.application.dashboard.AccountsSummary
import com.baraa.masroof.application.dashboard.OwnedAccount

fun resolveDashboardAccountsFleet(
    ownedAccounts: List<OwnedAccountUi>,
    fleet: AccountsSummary?,
): AccountsSummary =
    fleet ?: AccountsSummary(
        accounts = ownedAccounts.mapNotNull { account ->
            account.periodSummary?.let { summary ->
                OwnedAccount.from(
                    bank = account.bank,
                    maskedNumber = account.maskedNumber,
                    summary = summary,
                )
            }
        },
    )
