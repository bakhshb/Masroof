package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountSenderProfileCrossRef
import com.baraa.masroof.data.db.AccountSenderProfileDao
import com.baraa.masroof.data.db.SenderProfileDao
import com.baraa.masroof.data.db.SenderProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [SenderProfileDao] for unit tests. */
class FakeSenderProfileDao : SenderProfileDao {
    private val rows = MutableStateFlow<List<SenderProfileEntity>>(emptyList())
    private var idSeq = 0L

    override suspend fun insert(row: SenderProfileEntity): Long {
        idSeq += 1
        val saved = row.copy(id = idSeq)
        rows.value = rows.value + saved
        return idSeq
    }

    override suspend fun update(row: SenderProfileEntity) {
        rows.value = rows.value.map { if (it.id == row.id) row else it }
    }

    override suspend fun getById(id: Long): SenderProfileEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun findByKey(key: String): SenderProfileEntity? =
        rows.value.firstOrNull { it.normalizedSenderKey == key }

    override suspend fun getActive(): List<SenderProfileEntity> =
        rows.value.filter { it.active }.sortedBy { it.displaySender }

    override fun observeActive(): Flow<List<SenderProfileEntity>> =
        rows.map { list -> list.filter { it.active }.sortedBy { it.displaySender } }

    override suspend fun getAll(): List<SenderProfileEntity> =
        rows.value.sortedByDescending { it.updatedAt }

    override suspend fun deactivate(id: Long, updatedAt: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(active = false, updatedAt = updatedAt) else it
        }
    }

    override suspend fun activeNormalizedKeys(): List<String> =
        rows.value.filter { it.active }.map { it.normalizedSenderKey }
}

/** In-memory [AccountSenderProfileDao] for unit tests. */
class FakeAccountSenderProfileDao(
    private val profiles: FakeSenderProfileDao,
    private val ownedAccountIds: () -> Set<Long> = { emptySet() },
) : AccountSenderProfileDao {
    private val links = mutableListOf<AccountSenderProfileCrossRef>()

    override suspend fun insert(row: AccountSenderProfileCrossRef): Long {
        if (links.none { it.accountId == row.accountId && it.senderProfileId == row.senderProfileId }) {
            links += row
        }
        return 1L
    }

    override suspend fun delete(accountId: Long, senderProfileId: Long) {
        links.removeAll { it.accountId == accountId && it.senderProfileId == senderProfileId }
    }

    override suspend fun deleteAllForAccount(accountId: Long) {
        links.removeAll { it.accountId == accountId }
    }

    override suspend fun getForAccount(accountId: Long): List<AccountSenderProfileCrossRef> =
        links.filter { it.accountId == accountId }

    override suspend fun getForSender(senderProfileId: Long): List<AccountSenderProfileCrossRef> =
        links.filter { it.senderProfileId == senderProfileId }

    override suspend fun accountIdsForSender(senderProfileId: Long): List<Long> =
        links.filter { it.senderProfileId == senderProfileId }.map { it.accountId }

    override suspend fun senderIdsForAccount(accountId: Long): List<Long> =
        links.filter { it.accountId == accountId }.map { it.senderProfileId }

    override suspend fun activeOwnedSenderKeys(): List<String> {
        val owned = ownedAccountIds()
        return links
            .filter { it.accountId in owned }
            .mapNotNull { link ->
                profiles.getById(link.senderProfileId)
                    ?.takeIf { it.active }
                    ?.normalizedSenderKey
            }
            .distinct()
    }

    override suspend fun getAll(): List<AccountSenderProfileCrossRef> = links.toList()
}
