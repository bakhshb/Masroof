package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.dao.UserCorrectionDao
import com.baraa.masroof.data.room.mapper.UserCorrectionMapper
import com.baraa.masroof.domain.model.UserCorrection
import com.baraa.masroof.domain.repository.UserCorrectionRepository

class RoomUserCorrectionRepository(
    private val dao: UserCorrectionDao,
) : UserCorrectionRepository {
    override suspend fun save(correction: UserCorrection) {
        dao.insert(UserCorrectionMapper.toEntity(correction))
    }

    override suspend fun latestForRawSmsId(rawSmsId: String): UserCorrection? =
        dao.latestForRawSmsId(rawSmsId)?.let(UserCorrectionMapper::toDomain)

    override suspend fun listForRawSmsId(rawSmsId: String): List<UserCorrection> =
        dao.listForRawSmsId(rawSmsId).map(UserCorrectionMapper::toDomain)
}
