package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    ): Long {
        val n = now()
        val entity = FinancialAccountEntity(
            displayName = displayName,
            institutionName = institutionName,
            accountType = accountType,
            lastFourDigits = lastFourDigits?.takeIf { it.isNotBlank() },
            senderAliases = senderAliases.joinToString(","),
            isOwnedByUser = true,
            isActive = true,
            createdAt = n,
            updatedAt = n,
        )
        return dao.insert(entity)
    }

    override suspend fun update(account: FinancialAccount) {
        val n = now()
        dao.update(
            FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                lastFourDigits = account.lastFourDigits,
                senderAliases = account.senderAliases.joinToString(","),
                isOwnedByUser = account.isOwnedByUser,
                isActive = account.isActive,
                createdAt = n,
                updatedAt = n,
            )
        )
    }

    override suspend fun delete(account: FinancialAccount) {
        dao.delete(
            FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                lastFourDigits = account.lastFourDigits,
                senderAliases = account.senderAliases.joinToString(","),
                isOwnedByUser = account.isOwnedByUser,
                isActive = account.isActive,
                createdAt = 0L,
                updatedAt = now(),
            )
        )
    }
}

internal fun FinancialAccountEntity.toDomain(): FinancialAccount = FinancialAccount(
    id = id,
    displayName = displayName,
    institutionName = institutionName,
    accountType = accountType,
    lastFourDigits = lastFourDigits,
    senderAliases = if (senderAliases.isBlank()) emptyList()
                    else senderAliases.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    isOwnedByUser = isOwnedByUser,
    isActive = isActive,
)
