package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.ParsedEventDao
import com.baraa.masroof.data.room.mapper.ParsedEventMapper
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.repository.ParsedEventRecord
import com.baraa.masroof.domain.repository.ParsedEventRepository
import com.baraa.masroof.parsing.model.ParsedEventDetails

class RoomParsedEventRepository(
    private val dao: ParsedEventDao,
) : ParsedEventRepository {
    override suspend fun save(event: ParsedEvent, details: ParsedEventDetails) {
        val entity = ParsedEventMapper.toEntity(event, details)
        dao.replaceForRawSms(entity)
    }

    override suspend fun getById(id: String): ParsedEventRecord? =
        dao.getById(id)?.let(ParsedEventMapper::toRecord)

    override suspend fun findByRawSmsId(rawSmsId: String): ParsedEventRecord? =
        dao.findByRawSmsId(rawSmsId)?.let(ParsedEventMapper::toRecord)

    override suspend fun deleteByRawSmsId(rawSmsId: String) {
        dao.deleteByRawSmsId(rawSmsId)
    }
}
