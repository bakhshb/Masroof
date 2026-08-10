package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.RawSmsDao
import com.baraa.masroof.data.room.mapper.RawSmsMapper
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.repository.RawSmsInsertResult
import com.baraa.masroof.domain.repository.RawSmsRepository

class RoomRawSmsRepository(
    private val dao: RawSmsDao,
) : RawSmsRepository {
    override suspend fun insertIfAbsent(rawSms: RawSms): RawSmsInsertResult {
        if (dao.existsById(rawSms.id)) {
            return RawSmsInsertResult.AlreadyExists
        }
        val deviceId = rawSms.deviceMessageId
        if (deviceId != null && dao.findByDeviceMessageId(deviceId) != null) {
            return RawSmsInsertResult.AlreadyExists
        }
        val entity = RawSmsMapper.toEntity(rawSms)
        if (dao.findByDedupeKey(entity.dedupeKey) != null) {
            return RawSmsInsertResult.AlreadyExists
        }
        dao.insert(entity)
        return RawSmsInsertResult.Inserted
    }

    override suspend fun getById(id: String): RawSms? =
        dao.getById(id)?.let(RawSmsMapper::toDomain)

    override suspend fun existsById(id: String): Boolean = dao.existsById(id)

    override suspend fun findByDeviceMessageId(deviceMessageId: String): RawSms? =
        dao.findByDeviceMessageId(deviceMessageId)?.let(RawSmsMapper::toDomain)
}
