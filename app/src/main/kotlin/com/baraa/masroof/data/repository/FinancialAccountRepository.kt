package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.transaction.AccountLiquidityDefaults
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

interface FinancialAccountRepository {
    fun observeAll(): Flow<List<FinancialAccount>>
    suspend fun getActive(): List<FinancialAccount>
    suspend fun getOwnedActive(): List<FinancialAccount>
    suspend fun getById(id: Long): FinancialAccount?
    suspend fun add(
        displayName: String,
        accountType: AccountType,
        institutionName: String? = null,
        lastFourDigits: String? = null,
        senderAliases: List<String> = emptyList(),
        accountNature: AccountNature = AccountNature.defaultNatureFor(accountType),
        currency: Currency = Currency.SAR,
        openingBalance: BigDecimal = BigDecimal.ZERO,
        openingBalanceDate: Long = 0L,
        includeInNetWorth: Boolean = true,
        includeInLiquidity: Boolean = AccountLiquidityDefaults.defaultFor(accountType),
        notes: String? = null,
    ): Long
    suspend fun update(account: FinancialAccount)
    suspend fun delete(account: FinancialAccount)
}

class RoomFinancialAccountRepository(
    private val dao: FinancialAccountDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) : FinancialAccountRepository {

    override fun observeAll(): Flow<List<FinancialAccount>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActive(): List<FinancialAccount> =
        dao.getActive().map { it.toDomain() }

    override suspend fun getOwnedActive(): List<FinancialAccount> =
        dao.getOwnedActive().map { it.toDomain() }

    override suspend fun getById(id: Long): FinancialAccount? = dao.getById(id)?.toDomain()

    override suspend fun add(
        displayName: String,
        accountType: AccountType,
        institutionName: String?,
        lastFourDigits: String?,
        senderAliases: List<String>,
        accountNature: AccountNature,
        currency: Currency,
        openingBalance: BigDecimal,
        openingBalanceDate: Long,
        includeInNetWorth: Boolean,
        includeInLiquidity: Boolean,
        notes: String?,
    ): Long {
        val n = now()
        val entity = FinancialAccountEntity(
            displayName = displayName,
            institutionName = institutionName,
            accountType = accountType,
            accountNature = accountNature,
            lastFourDigits = sanitizeLastFour(lastFourDigits),
            senderAliases = senderAliases.joinToString(","),
            currency = currency,
            openingBalance = openingBalance,
            openingBalanceDate = openingBalanceDate,
            includeInNetWorth = includeInNetWorth,
            includeInLiquidity = includeInLiquidity,
            isOwnedByUser = true,
            systemAccountKey = null,
            isActive = true,
            notes = notes?.takeIf { it.isNotBlank() },
            createdAt = n,
            updatedAt = n,
        )
        return dao.insert(entity)
    }

    override suspend fun update(account: FinancialAccount) {
        require(account.systemAccountKey == null) { "System accounts cannot be edited" }
        val n = now()
        dao.update(
            FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                accountNature = account.accountNature,
                lastFourDigits = sanitizeLastFour(account.lastFourDigits),
                senderAliases = account.senderAliases.joinToString(","),
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
                updatedAt = n,
            )
        )
    }

    override suspend fun delete(account: FinancialAccount) {
        require(account.systemAccountKey == null) { "System accounts cannot be deleted" }
        dao.delete(
            FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                accountNature = account.accountNature,
                lastFourDigits = sanitizeLastFour(account.lastFourDigits),
                senderAliases = account.senderAliases.joinToString(","),
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
            )
        )
    }
}

private fun sanitizeLastFour(value: String?): String? =
    value?.trim()?.takeIf { it.length == 4 && it.all(Char::isDigit) }

internal fun FinancialAccountEntity.toDomain(): FinancialAccount = FinancialAccount(
    id = id,
    displayName = displayName,
    institutionName = institutionName,
    accountType = accountType,
    accountNature = accountNature,
    lastFourDigits = lastFourDigits,
    senderAliases = if (senderAliases.isBlank()) emptyList()
                    else senderAliases.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    currency = currency,
    openingBalance = openingBalance,
    openingBalanceDate = openingBalanceDate,
    includeInNetWorth = includeInNetWorth,
    includeInLiquidity = includeInLiquidity,
    isOwnedByUser = isOwnedByUser,
    systemAccountKey = systemAccountKey,
    isActive = isActive,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
