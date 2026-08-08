package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency

/** Creates each hidden system account once; key lookup makes initialization idempotent. */
class SystemAccountSeeder(
    private val accountDao: FinancialAccountDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun seed() {
        SystemAccountKey.entries.forEach { key ->
            if (accountDao.getSystemAccount(key.name) == null) accountDao.insert(create(key))
        }
    }

    suspend fun accountId(key: SystemAccountKey): Long {
        accountDao.getSystemAccount(key.name)?.let { return it.id }
        // Seed lazily if app startup seed raced or failed — still fail clearly if absent.
        seed()
        return requireNotNull(accountDao.getSystemAccount(key.name)) {
            "System account missing: ${key.name}"
        }.id
    }

    private fun create(key: SystemAccountKey): FinancialAccountEntity {
        val time = now()
        val nature = when (key) {
            SystemAccountKey.OPENING_BALANCE_EQUITY,
            SystemAccountKey.INCOME_CLEARING,
            SystemAccountKey.REFUND_CLEARING -> AccountNature.LIABILITY
            SystemAccountKey.EXPENSE_CLEARING,
            SystemAccountKey.BANK_FEE_EXPENSE,
            SystemAccountKey.UNASSIGNED_CLEARING,
            SystemAccountKey.CASH_ON_HAND -> AccountNature.ASSET
        }
        val isCashOnHand = key == SystemAccountKey.CASH_ON_HAND
        return FinancialAccountEntity(
            displayName = if (isCashOnHand) "نقد في اليد" else key.name,
            institutionName = null,
            accountType = if (isCashOnHand) AccountType.CASH else AccountType.OTHER,
            accountNature = nature,
            currency = Currency.SAR,
            openingBalance = java.math.BigDecimal.ZERO,
            openingBalanceDate = 0L,
            includeInNetWorth = isCashOnHand,
            includeInLiquidity = isCashOnHand,
            isOwnedByUser = false,
            systemAccountKey = key,
            isActive = true,
            notes = null,
            createdAt = time,
            updatedAt = time,
        )
    }
}
