package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import java.math.BigDecimal

/**
 * Applies a new credit limit from an informational SMS onto a credit-card
 * account. Never creates journals or changes balances.
 */
object CreditLimitUpdater {
    suspend fun applyToAccount(
        account: FinancialAccount,
        newLimit: BigDecimal,
        dao: FinancialAccountDao,
        now: () -> Long = { System.currentTimeMillis() },
    ): Boolean {
        if (newLimit.signum() <= 0) return false
        if (account.creditLimit?.compareTo(newLimit) == 0) return false
        dao.update(
            FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                accountNature = account.accountNature,
                currency = account.currency,
                openingBalance = account.openingBalance,
                openingBalanceDate = account.openingBalanceDate,
                includeInNetWorth = account.includeInNetWorth,
                includeInLiquidity = account.includeInLiquidity,
                isOwnedByUser = account.isOwnedByUser,
                systemAccountKey = account.systemAccountKey,
                isActive = account.isActive,
                notes = account.notes,
                createdAt = account.createdAt,
                updatedAt = now(),
                creditLimit = newLimit,
                openingBalanceKind = account.openingBalanceKind,
            ),
        )
        return true
    }
}
