package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.SenderInstitutionMappingDao
import com.baraa.masroof.data.db.SenderInstitutionMappingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/** In-memory DAO used only by JVM unit tests. */
class FakeSenderInstitutionMappingDao : SenderInstitutionMappingDao {
    private val rows = MutableStateFlow<List<SenderInstitutionMappingEntity>>(emptyList())
    private val idSeq = AtomicLong()

    override fun observeAll(): Flow<List<SenderInstitutionMappingEntity>> = rows.map { it.sortedWith(compareByDescending<SenderInstitutionMappingEntity> { it.isActive }.thenByDescending { it.lastConfirmedAt }) }
    override suspend fun getActive(): List<SenderInstitutionMappingEntity> = rows.value.filter { it.isActive }
    override suspend fun findByKey(key: String): SenderInstitutionMappingEntity? = rows.value.firstOrNull { it.senderKey == key }
    override suspend fun insert(mapping: SenderInstitutionMappingEntity): Long {
        val withId = mapping.copy(id = if (mapping.id == 0L) idSeq.incrementAndGet() else mapping.id)
        val existing = rows.value.toMutableList()
        val i = existing.indexOfFirst { it.senderKey == withId.senderKey }
        if (i >= 0) existing[i] = withId else existing.add(withId)
        rows.value = existing
        return withId.id
    }
    override suspend fun update(mapping: SenderInstitutionMappingEntity): Int {
        val list = rows.value.toMutableList()
        val i = list.indexOfFirst { it.id == mapping.id }
        if (i < 0) return 0
        list[i] = mapping
        rows.value = list
        return 1
    }
    override suspend fun setActive(id: Long, active: Boolean, lastConfirmedAt: Long): Int {
        val list = rows.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return 0
        list[i] = list[i].copy(isActive = active, lastConfirmedAt = lastConfirmedAt)
        rows.value = list
        return 1
    }
    override suspend fun deleteById(id: Long): Int {
        val list = rows.value
        val newList = list.filterNot { it.id == id }
        val removed = list.size - newList.size
        rows.value = newList
        return removed
    }
}
