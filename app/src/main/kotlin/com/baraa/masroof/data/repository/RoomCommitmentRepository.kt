package com.baraa.masroof.data.repository

import com.baraa.masroof.data.room.MasroofDatabase
import com.baraa.masroof.data.room.dao.CommitmentDao
import com.baraa.masroof.data.room.mapper.CommitmentMapper
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.repository.CommitmentRepository

class RoomCommitmentRepository(
    private val dao: CommitmentDao,
) : CommitmentRepository {
    override suspend fun create(commitment: Commitment) {
        dao.insert(CommitmentMapper.toEntity(commitment))
    }

    override suspend fun update(commitment: Commitment) {
        dao.update(CommitmentMapper.toEntity(commitment))
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun setActive(id: String, active: Boolean) {
        val existing = dao.get(id) ?: return
        dao.update(
            existing.copy(
                active = active,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun get(id: String): Commitment? =
        dao.get(id)?.let(CommitmentMapper::toDomain)

    override suspend fun getBySourceTransactionId(sourceTransactionId: String): Commitment? =
        dao.getBySourceTransactionId(sourceTransactionId)?.let(CommitmentMapper::toDomain)

    override suspend fun listAll(): List<Commitment> =
        dao.listAll().map(CommitmentMapper::toDomain)

    override suspend fun listActive(): List<Commitment> =
        dao.listActive().map(CommitmentMapper::toDomain)

    companion object {
        fun from(database: MasroofDatabase): RoomCommitmentRepository =
            RoomCommitmentRepository(dao = database.commitmentDao())
    }
}
