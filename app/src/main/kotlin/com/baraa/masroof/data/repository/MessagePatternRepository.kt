package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.MessagePatternDefinitionDao
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternFieldDefinitionDao
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternOrigin
import com.baraa.masroof.sms.DiscoveredMessagePattern
import com.baraa.masroof.sms.SuggestedPatternField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class MessagePattern(
    val definition: MessagePatternDefinitionEntity,
    val fields: List<PatternFieldDefinitionEntity>,
)

class MessagePatternRepository(
    private val definitionDao: MessagePatternDefinitionDao,
    private val fieldDao: PatternFieldDefinitionDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun getForSender(senderProfileId: Long): List<MessagePattern> = withContext(Dispatchers.IO) {
        definitionDao.getForSender(senderProfileId).map { def ->
            MessagePattern(def, fieldDao.getForPattern(def.id))
        }
    }

    suspend fun getByStatus(status: MessagePatternStatus): List<MessagePattern> =
        withContext(Dispatchers.IO) {
            definitionDao.getByStatus(status).map { MessagePattern(it, fieldDao.getForPattern(it.id)) }
        }

    suspend fun getImportableForSender(senderProfileId: Long): List<MessagePattern> =
        withContext(Dispatchers.IO) {
            definitionDao.getForSender(senderProfileId)
                .filter {
                    it.status == MessagePatternStatus.APPROVED ||
                        it.status == MessagePatternStatus.DEPRECATED
                }
                .map { MessagePattern(it, fieldDao.getForPattern(it.id)) }
        }

    suspend fun getImportable(): List<MessagePattern> = withContext(Dispatchers.IO) {
        definitionDao.getImportable().map { MessagePattern(it, fieldDao.getForPattern(it.id)) }
    }

    fun observeUnknown(): Flow<List<MessagePatternDefinitionEntity>> = definitionDao.observeUnknown()

    suspend fun countUnknown(): Int = withContext(Dispatchers.IO) { definitionDao.countUnknown() }

    suspend fun findBySignature(senderProfileId: Long, signature: String): MessagePattern? =
        withContext(Dispatchers.IO) {
            val def = definitionDao.findLatestBySignature(senderProfileId, signature) ?: return@withContext null
            MessagePattern(def, fieldDao.getForPattern(def.id))
        }

    suspend fun saveDiscovered(
        senderProfileId: Long,
        discovered: DiscoveredMessagePattern,
        status: MessagePatternStatus,
        userFriendlyName: String? = null,
        fields: List<SuggestedPatternField> = discovered.suggestedFields,
        origin: PatternOrigin = PatternOrigin.USER_TRAINED,
        userConfirmed: Boolean = status == MessagePatternStatus.APPROVED ||
            status == MessagePatternStatus.IGNORED,
    ): MessagePattern = withContext(Dispatchers.IO) {
        val existing = definitionDao.findLatestBySignature(senderProfileId, discovered.signature)
        val ts = now()
        if (existing == null) {
            val id = definitionDao.insert(
                MessagePatternDefinitionEntity(
                    senderProfileId = senderProfileId,
                    userFriendlyName = userFriendlyName ?: discovered.friendlyNameHint,
                    normalizedSignature = discovered.signature,
                    status = status,
                    version = 1,
                    origin = origin,
                    confidence = if (userConfirmed) 100 else 40,
                    userConfirmed = userConfirmed,
                    exampleCount = discovered.messageCount,
                    activeFrom = if (status == MessagePatternStatus.APPROVED) ts else null,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
            persistFields(id, fields)
            MessagePattern(definitionDao.getById(id)!!, fieldDao.getForPattern(id))
        } else {
            val updated = existing.copy(
                userFriendlyName = userFriendlyName ?: existing.userFriendlyName,
                status = status,
                userConfirmed = userConfirmed || existing.userConfirmed,
                exampleCount = existing.exampleCount + discovered.messageCount,
                activeFrom = when {
                    status == MessagePatternStatus.APPROVED && existing.activeFrom == null -> ts
                    else -> existing.activeFrom
                },
                deprecatedAt = if (status == MessagePatternStatus.DEPRECATED) ts else existing.deprecatedAt,
                updatedAt = ts,
                confidence = if (userConfirmed) 100 else existing.confidence,
            )
            definitionDao.update(updated)
            if (fields.isNotEmpty()) {
                fieldDao.deleteForPattern(existing.id)
                persistFields(existing.id, fields)
            }
            MessagePattern(updated, fieldDao.getForPattern(existing.id))
        }
    }

    suspend fun setStatus(patternId: Long, status: MessagePatternStatus) = withContext(Dispatchers.IO) {
        val existing = definitionDao.getById(patternId) ?: return@withContext
        val ts = now()
        definitionDao.update(
            existing.copy(
                status = status,
                userConfirmed = true,
                activeFrom = if (status == MessagePatternStatus.APPROVED) {
                    existing.activeFrom ?: ts
                } else {
                    existing.activeFrom
                },
                deprecatedAt = if (status == MessagePatternStatus.DEPRECATED) ts else existing.deprecatedAt,
                updatedAt = ts,
                confidence = 100,
            ),
        )
    }

    suspend fun replaceFields(patternId: Long, fields: List<SuggestedPatternField>) =
        withContext(Dispatchers.IO) {
            fieldDao.deleteForPattern(patternId)
            persistFields(patternId, fields)
        }

    /**
     * Ensure an UNKNOWN definition exists for an unmatched signature (never silent drop).
     */
    suspend fun ensureUnknown(
        senderProfileId: Long,
        signature: String,
        friendlyName: String,
    ): MessagePattern = withContext(Dispatchers.IO) {
        val existing = definitionDao.findLatestBySignature(senderProfileId, signature)
        if (existing != null) {
            return@withContext MessagePattern(existing, fieldDao.getForPattern(existing.id))
        }
        val ts = now()
        val id = definitionDao.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = senderProfileId,
                userFriendlyName = friendlyName,
                normalizedSignature = signature,
                status = MessagePatternStatus.UNKNOWN,
                version = 1,
                origin = PatternOrigin.USER_TRAINED,
                confidence = 20,
                userConfirmed = false,
                exampleCount = 1,
                createdAt = ts,
                updatedAt = ts,
            ),
        )
        MessagePattern(definitionDao.getById(id)!!, emptyList())
    }

    private suspend fun persistFields(patternId: Long, fields: List<SuggestedPatternField>) {
        if (fields.isEmpty()) return
        fieldDao.insertAll(
            fields.map { f ->
                PatternFieldDefinitionEntity(
                    patternId = patternId,
                    canonicalField = f.canonicalField,
                    sourceLabel = f.sourceLabel,
                    extractionStrategy = f.extractionStrategy,
                    required = f.required,
                    role = f.role,
                    valueType = f.valueType,
                )
            },
        )
    }
}
