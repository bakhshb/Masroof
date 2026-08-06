package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierDao
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Lightweight in-memory AccountIdentifierDao for unit tests. */
class FakeIdentifierDao : AccountIdentifierDao {
    private val rows = MutableStateFlow<List<AccountIdentifierEntity>>(emptyList())
    private var idSeq = 0L
    override fun observeByAccount(accountId: Long): Flow<List<AccountIdentifierEntity>> = rows.map { it.filter { r -> r.accountId == accountId } }
    override fun observeAll(): Flow<List<AccountIdentifierEntity>> = rows
    override suspend fun getByAccount(accountId: Long): List<AccountIdentifierEntity> = rows.value.filter { it.accountId == accountId }
    override suspend fun getById(id: Long): AccountIdentifierEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun getActive(): List<AccountIdentifierEntity> = rows.value.filter { it.isActive }
    override suspend fun findByValue(value: String): AccountIdentifierEntity? = rows.value.firstOrNull { it.normalizedValue == value && it.isActive }
    override suspend fun findByTypeAndValue(type: AccountIdentifierType, value: String): AccountIdentifierEntity? =
        rows.value.firstOrNull { it.identifierType == type && it.normalizedValue == value && it.isActive }
    override suspend fun getByType(type: AccountIdentifierType): List<AccountIdentifierEntity> = rows.value.filter { it.identifierType == type }
    override suspend fun insert(identifier: AccountIdentifierEntity): Long {
        idSeq += 1
        val saved = identifier.copy(id = idSeq)
        rows.value = rows.value + saved
        return idSeq
    }
    override suspend fun update(identifier: AccountIdentifierEntity): Int {
        val list = rows.value.toMutableList()
        val i = list.indexOfFirst { it.id == identifier.id }
        if (i < 0) return 0
        list[i] = identifier
        rows.value = list
        return 1
    }
    override suspend fun delete(identifier: AccountIdentifierEntity): Int {
        val list = rows.value
        val newList = list.filterNot { it.id == identifier.id }
        val removed = list.size - newList.size
        rows.value = newList
        return removed
    }
}
