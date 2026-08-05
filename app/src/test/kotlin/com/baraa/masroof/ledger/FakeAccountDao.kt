package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight in-memory FinancialAccountDao for unit tests. */
class FakeAccountDao(initial: List<FinancialAccountEntity> = emptyList()) : FinancialAccountDao {
    private val accounts = linkedMapOf<Long, FinancialAccountEntity>().apply {
        for (a in initial) put(a.id, a)
    }
    override fun observeAll(): Flow<List<FinancialAccountEntity>> = MutableStateFlow(accounts.values.toList())
    override suspend fun getActive(): List<FinancialAccountEntity> = accounts.values.filter { it.isActive }
    override suspend fun getOwnedActive(): List<FinancialAccountEntity> = accounts.values.filter { it.isOwnedByUser && it.isActive }
    override suspend fun getById(id: Long): FinancialAccountEntity? = accounts[id]
    override suspend fun getSystemAccount(key: String): FinancialAccountEntity? = accounts.values.firstOrNull { it.systemAccountKey?.name == key }
    override suspend fun getSystemAccounts(): List<FinancialAccountEntity> = accounts.values.filter { it.systemAccountKey != null }
    override suspend fun insert(account: FinancialAccountEntity): Long {
        val id = if (account.id == 0L) (accounts.keys.maxOrNull() ?: 0L) + 1 else account.id
        accounts[id] = account.copy(id = id)
        return id
    }
    override suspend fun update(account: FinancialAccountEntity): Int {
        if (!accounts.containsKey(account.id)) return 0
        accounts[account.id] = account
        return 1
    }
    override suspend fun delete(account: FinancialAccountEntity): Int {
        val removed = accounts.remove(account.id) != null
        return if (removed) 1 else 0
    }
}
