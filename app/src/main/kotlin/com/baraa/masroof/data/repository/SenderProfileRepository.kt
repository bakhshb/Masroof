package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.AccountSenderProfileCrossRef
import com.baraa.masroof.data.db.AccountSenderProfileDao
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.SenderInstitutionMappingDao
import com.baraa.masroof.data.db.SenderProfileDao
import com.baraa.masroof.data.db.SenderProfileEntity
import com.baraa.masroof.sms.SenderNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Domain view of a trained SMS sender. */
data class SenderProfile(
    val id: Long,
    val displaySender: String,
    val normalizedSenderKey: String,
    val displayInstitutionName: String?,
    val active: Boolean,
)

class SenderProfileRepository(
    private val dao: SenderProfileDao,
    private val accountSenderDao: AccountSenderProfileDao,
    private val accountDao: FinancialAccountDao,
    private val mappingDao: SenderInstitutionMappingDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeActive(): Flow<List<SenderProfileEntity>> = dao.observeActive()

    suspend fun getActive(): List<SenderProfile> = withContext(Dispatchers.IO) {
        dao.getActive().map { it.toDomain() }
    }

    suspend fun getById(id: Long): SenderProfile? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun findByRawSender(rawSender: String?): SenderProfile? = withContext(Dispatchers.IO) {
        val key = SenderNormalizer.normalize(rawSender) ?: return@withContext null
        dao.findByKey(key)?.toDomain()
    }

    /**
     * Upsert a profile for an Android SMS sender. Does not create account links.
     */
    suspend fun upsertFromSmsSender(
        rawSender: String,
        displayInstitutionName: String? = null,
    ): SenderProfile = withContext(Dispatchers.IO) {
        val key = SenderNormalizer.normalize(rawSender) ?: error("sender required")
        val display = rawSender.trim().ifBlank { key }
        val existing = dao.findByKey(key)
        val ts = now()
        if (existing == null) {
            val institution = displayInstitutionName
                ?: mappingDao?.getActive()?.firstOrNull {
                    SenderNormalizer.normalize(it.senderKey) == key
                }?.institutionName
            val id = dao.insert(
                SenderProfileEntity(
                    displaySender = display,
                    normalizedSenderKey = key,
                    displayInstitutionName = institution,
                    active = true,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
            dao.getById(id)!!.toDomain()
        } else {
            val updated = existing.copy(
                displaySender = display.takeIf { it.isNotBlank() } ?: existing.displaySender,
                displayInstitutionName = displayInstitutionName ?: existing.displayInstitutionName,
                active = true,
                updatedAt = ts,
            )
            dao.update(updated)
            updated.toDomain()
        }
    }

    suspend fun associateAccount(accountId: Long, senderProfileId: Long) = withContext(Dispatchers.IO) {
        accountSenderDao.insert(
            AccountSenderProfileCrossRef(
                accountId = accountId,
                senderProfileId = senderProfileId,
                createdAt = now(),
            ),
        )
    }

    suspend fun dissociateAccount(accountId: Long, senderProfileId: Long) = withContext(Dispatchers.IO) {
        accountSenderDao.delete(accountId, senderProfileId)
    }

    suspend fun setAccountSenders(accountId: Long, senderProfileIds: Collection<Long>) =
        withContext(Dispatchers.IO) {
            accountSenderDao.deleteAllForAccount(accountId)
            val ts = now()
            for (sid in senderProfileIds.distinct()) {
                accountSenderDao.insert(
                    AccountSenderProfileCrossRef(accountId, sid, ts),
                )
            }
        }

    suspend fun profilesForAccount(accountId: Long): List<SenderProfile> = withContext(Dispatchers.IO) {
        accountSenderDao.senderIdsForAccount(accountId)
            .mapNotNull { dao.getById(it)?.toDomain() }
    }

    suspend fun accountsForSenderKey(rawOrKey: String?): List<FinancialAccount> =
        withContext(Dispatchers.IO) {
            val key = SenderNormalizer.normalize(rawOrKey) ?: return@withContext emptyList()
            val profile = dao.findByKey(key) ?: return@withContext emptyList()
            accountSenderDao.accountIdsForSender(profile.id)
                .mapNotNull { accountDao.getById(it)?.toDomain() }
                .filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
        }

    /** Keys for import allowlist: active profiles linked to owned accounts. */
    suspend fun activeOwnedSenderKeys(): Set<String> = withContext(Dispatchers.IO) {
        accountSenderDao.activeOwnedSenderKeys().filter { it.isNotBlank() }.toSet()
    }

    /** Normalized sender key → owned account ids (for rule-engine context). */
    suspend fun accountsBySenderKeyMap(): Map<String, Set<Long>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, MutableSet<Long>>()
        for (profile in dao.getActive()) {
            val ids = accountSenderDao.accountIdsForSender(profile.id)
                .mapNotNull { accountDao.getById(it) }
                .filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
                .map { it.id }
            if (ids.isNotEmpty()) {
                result.getOrPut(profile.normalizedSenderKey) { mutableSetOf() }.addAll(ids)
            }
        }
        result.mapValues { it.value.toSet() }
    }

    /** All active profile keys (trained senders), even before account link. */
    suspend fun activeProfileKeys(): Set<String> = withContext(Dispatchers.IO) {
        dao.activeNormalizedKeys().filter { it.isNotBlank() }.toSet()
    }

    suspend fun deactivate(id: Long) = withContext(Dispatchers.IO) {
        dao.deactivate(id, now())
    }

    private fun SenderProfileEntity.toDomain() = SenderProfile(
        id = id,
        displaySender = displaySender,
        normalizedSenderKey = normalizedSenderKey,
        displayInstitutionName = displayInstitutionName,
        active = active,
    )
}
