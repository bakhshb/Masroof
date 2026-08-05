package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.SenderInstitutionMappingDao
import com.baraa.masroof.data.db.SenderInstitutionMappingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Read/write API for sender-to-institution mappings. Persists only
 * user-confirmed friendly names; never persists raw SMS bodies, amounts,
 * or full identifiers.
 */
interface SenderInstitutionMappingRepository {
    fun observeAll(): Flow<List<SenderInstitutionMappingEntity>>
    suspend fun getActive(): List<SenderInstitutionMappingEntity>
    suspend fun findByKey(key: String): SenderInstitutionMappingEntity?
    suspend fun upsert(sender: String, institutionName: String): Long
    suspend fun setActive(id: Long, active: Boolean)
    suspend fun delete(id: Long)
}

class RoomSenderInstitutionMappingRepository(
    private val dao: SenderInstitutionMappingDao,
    private val senderNormalizer: (String?) -> String?,
) : SenderInstitutionMappingRepository {
    override fun observeAll(): Flow<List<SenderInstitutionMappingEntity>> = dao.observeAll()
    override suspend fun getActive(): List<SenderInstitutionMappingEntity> = withContext(Dispatchers.IO) { dao.getActive() }
    override suspend fun findByKey(key: String): SenderInstitutionMappingEntity? = withContext(Dispatchers.IO) { dao.findByKey(key) }
    override suspend fun upsert(sender: String, institutionName: String): Long = withContext(Dispatchers.IO) {
        val key = senderNormalizer(sender) ?: return@withContext -1L
        if (institutionName.isBlank()) return@withContext -1L
        val now = System.currentTimeMillis()
        val existing = dao.findByKey(key)
        return@withContext if (existing != null) {
            dao.insert(existing.copy(institutionName = institutionName, isActive = true, confirmationCount = existing.confirmationCount + 1, lastConfirmedAt = now))
            existing.id
        } else {
            val id = dao.insert(SenderInstitutionMappingEntity(senderKey = key, institutionName = institutionName, isActive = true, confirmationCount = 1, lastConfirmedAt = now, createdAt = now))
            id
        }
    }
    override suspend fun setActive(id: Long, active: Boolean): Unit = withContext(Dispatchers.IO) { dao.setActive(id, active, System.currentTimeMillis()) }
    override suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) { dao.deleteById(id) }
}
