package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.SenderMessagePatternDao
import com.baraa.masroof.data.db.SenderMessagePatternEntity
import com.baraa.masroof.data.db.SenderMessagePatternKind
import com.baraa.masroof.sms.DiscoveredSmsPattern
import com.baraa.masroof.sms.LearnedSmsFeatures
import com.baraa.masroof.sms.SenderMessagePatternLearner
import com.baraa.masroof.sms.SenderNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Legacy flat teach patterns (structureKey + label bags).
 *
 * **Deprecated for production import.** Prefer [MessagePatternRepository] /
 * [MessagePatternDefinitionEntity]. Table retained for historical rows already
 * migrated into definitions; do not wire into [SmsImportOrchestrator].
 */
@Deprecated("Use MessagePatternRepository / MessagePatternDefinition instead")
class SenderMessagePatternRepository(
    private val dao: SenderMessagePatternDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    data class SaveResult(
        val savedCount: Int,
        val senderKeys: Set<String>,
    )

    data class DiscoveredPatternSelection(
        val senderKey: String,
        val structureKey: String,
        val features: LearnedSmsFeatures,
        val exampleCount: Int,
        val kind: SenderMessagePatternKind = SenderMessagePatternKind.INCLUDE_TRANSACTION,
    )

    suspend fun activeIncludeSenderKeys(): Set<String> = withContext(Dispatchers.IO) {
        dao.activeIncludeSenderKeys().filter { it.isNotBlank() }.toSet()
    }

    suspend fun getActive(): List<SenderMessagePatternEntity> = withContext(Dispatchers.IO) {
        dao.getActive()
    }

    suspend fun getActiveInclude(): List<SenderMessagePatternEntity> = withContext(Dispatchers.IO) {
        dao.getActiveByKind(SenderMessagePatternKind.INCLUDE_TRANSACTION)
    }

    suspend fun getActiveIgnore(): List<SenderMessagePatternEntity> = withContext(Dispatchers.IO) {
        dao.getActiveByKind(SenderMessagePatternKind.IGNORE_AUTH)
    }

    suspend fun getActiveIncludeForSender(senderKey: String): List<SenderMessagePatternEntity> =
        withContext(Dispatchers.IO) {
            dao.getActiveBySenderAndKind(senderKey, SenderMessagePatternKind.INCLUDE_TRANSACTION)
        }

    suspend fun amountLabelsForSender(senderKey: String): Set<String> = withContext(Dispatchers.IO) {
        getActiveIncludeForSender(senderKey).flatMap { it.amountLabels }.toSet()
    }

    /**
     * Persist user-selected discovered styles for their senders.
     * Upserts by (senderKey, structureKey, kind); merges features only for the same style.
     */
    suspend fun saveSelectedPatterns(
        selections: List<DiscoveredPatternSelection>,
    ): SaveResult = withContext(Dispatchers.IO) {
        var saved = 0
        val senders = linkedSetOf<String>()
        for (selection in selections) {
            val senderKey = SenderNormalizer.normalize(selection.senderKey)
                ?: selection.senderKey.trim().lowercase()
            if (senderKey.isBlank()) continue
            val structureKey = selection.structureKey.ifBlank {
                SenderMessagePatternLearner.structureKeyFromFeatures(selection.features)
            }
            upsertMerged(
                senderKey = senderKey,
                structureKey = structureKey,
                kind = selection.kind,
                features = selection.features,
                addedExamples = selection.exampleCount.coerceAtLeast(1),
            )
            saved++
            senders += senderKey
        }
        SaveResult(savedCount = saved, senderKeys = senders)
    }

    fun selectionFromCluster(cluster: DiscoveredSmsPattern): DiscoveredPatternSelection =
        DiscoveredPatternSelection(
            senderKey = cluster.senderKey,
            structureKey = cluster.structureKey,
            features = cluster.features,
            exampleCount = cluster.messageCount,
            kind = SenderMessagePatternKind.INCLUDE_TRANSACTION,
        )

    suspend fun deactivate(id: Long) = withContext(Dispatchers.IO) {
        dao.deactivate(id, now())
    }

    private suspend fun upsertMerged(
        senderKey: String,
        structureKey: String,
        kind: SenderMessagePatternKind,
        features: LearnedSmsFeatures,
        addedExamples: Int,
    ) {
        val existing = dao.find(senderKey, structureKey, kind)
        val ts = now()
        if (existing == null) {
            dao.insert(
                SenderMessagePatternEntity(
                    senderKey = senderKey,
                    structureKey = structureKey,
                    accountId = null,
                    kind = kind,
                    amountLabels = features.amountLabels.toList().sorted(),
                    typeCues = features.typeCues.toList().sorted(),
                    lineLabels = features.lineLabels.toList().sorted(),
                    minScore = SenderMessagePatternLearner.defaultMinScore(features),
                    exampleCount = addedExamples,
                    active = true,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        } else {
            val merged = SenderMessagePatternLearner.merge(
                LearnedSmsFeatures(
                    amountLabels = existing.amountLabels.toSet(),
                    typeCues = existing.typeCues.toSet(),
                    lineLabels = existing.lineLabels.toSet(),
                ),
                features,
            )
            dao.update(
                existing.copy(
                    amountLabels = merged.amountLabels.toList().sorted(),
                    typeCues = merged.typeCues.toList().sorted(),
                    lineLabels = merged.lineLabels.toList().sorted(),
                    minScore = SenderMessagePatternLearner.defaultMinScore(merged),
                    exampleCount = existing.exampleCount + addedExamples,
                    active = true,
                    updatedAt = ts,
                ),
            )
        }
    }
}
