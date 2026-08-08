package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternFamilyEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternDuplicateMerger
import com.baraa.masroof.data.db.PatternFieldDefinitionDao
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternVariantAnchorDao
import com.baraa.masroof.data.db.PatternVariantAnchorEntity
import com.baraa.masroof.data.db.SenderProfileEntity
import com.baraa.masroof.data.repository.MessagePatternRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The architecture invariant tests. The same SMS body that trained a pattern
 * must match that pattern after persistence, and a structurally equivalent
 * body with different values must match the same persisted pattern.
 */
class TrainingPersistenceImportRoundTripTest {

    /**
     * The mandatory round-trip: train pattern from body A, persist it,
     * reload it, run the same body A through the production import matcher.
     * Result MUST be EXACT_MATCH.
     */
    @Test
    fun sameSmsThatTrainedPatternMatchesAfterPersistence() = runBlocking {
        val repo = buildRepo()
        val body = trainingPosBody
        val saved = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(body),
            status = MessagePatternStatus.APPROVED,
        )
        assertTrue(saved.definition.id > 0L)
        assertTrue(saved.definition.templateText!!.isNotBlank())
        assertEquals(NORMALIZATION_VERSION, saved.definition.normalizationVersion)

        val reloaded = repo.getForSender(1L).single { it.definition.id == saved.definition.id }
        val outcome = TemplateResolutionService.resolve(
            sender = "BankX",
            body = body,
            smsTimestampMillis = 1L,
            patterns = listOf(reloaded),
        )
        assertTrue(
            "expected Matched for the same body that trained the pattern, was $outcome",
            outcome is TemplateResolutionResult.Matched,
        )
        outcome as TemplateResolutionResult.Matched
        assertNotNull(outcome.parsed.amount)
        assertEquals(0, "55.00".compareTo(outcome.parsed.amount!!.toPlainString()))
    }

    /**
     * The structural-equivalence round-trip: same structural message with
     * different values MUST match the same persisted pattern.
     */
    @Test
    fun structurallyEquivalentSmsWithDifferentValuesMatchesSamePattern() = runBlocking {
        val repo = buildRepo()
        val trainBody = trainingPosBody
        val saved = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(trainBody),
            status = MessagePatternStatus.APPROVED,
        )
        val reloaded = repo.getForSender(1L).single { it.definition.id == saved.definition.id }

        // Sanity: A and B share one structural signature.
        assertEquals(
            SmsStructureNormalizer.signatureFromBody(trainBody),
            SmsStructureNormalizer.signatureFromBody(equivalentPosBody),
        )

        val outcome = TemplateResolutionService.resolve(
            sender = "BankX",
            body = equivalentPosBody,
            smsTimestampMillis = 1L,
            patterns = listOf(reloaded),
        )
        assertTrue(
            "expected Matched for structurally-equivalent body with different values, was $outcome",
            outcome is TemplateResolutionResult.Matched,
        )
        outcome as TemplateResolutionResult.Matched
        assertEquals(0, "129.50".compareTo(outcome.parsed.amount!!.toPlainString()))
    }

    /**
     * Re-training the same sender with the same message must not duplicate
     * the row. The repository upserts by canonical signature.
     */
    @Test
    fun trainingSameSenderTwiceIsIdempotent() = runBlocking {
        val repo = buildRepo()
        val body = trainingPosBody
        val first = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(body, messageCount = 3),
            status = MessagePatternStatus.APPROVED,
        )
        val second = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(body, messageCount = 2),
            status = MessagePatternStatus.APPROVED,
        )
        assertEquals(first.definition.id, second.definition.id)
        assertEquals(1, repo.getForSender(1L).size)
    }

    /**
     * Known-sender messages whose structure does NOT match any saved pattern
     * must produce an UNKNOWN pattern candidate, never a silent drop.
     */
    @Test
    fun knownSenderUnmatchedStructureProducesUnknownCandidate() = runBlocking {
        val repo = buildRepo()
        val trainedBody = trainingPosBody
        repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(trainedBody),
            status = MessagePatternStatus.APPROVED,
        )

        val unknownBody = """
            حوالة صادرة
            من حساب: 1234
            إلى حساب: 5678
            المبلغ: 100.00 SAR
            التاريخ: 08-08-2026
        """.trimIndent()

        // First, confirm the body is genuinely structurally different.
        assertNotEquals(
            SmsStructureNormalizer.signatureFromBody(trainedBody),
            SmsStructureNormalizer.signatureFromBody(unknownBody),
        )

        val saved = repo.ensureUnknown(
            senderProfileId = 1L,
            signature = SmsStructureNormalizer.signatureFromBody(unknownBody),
            friendlyName = "تحويل صادر",
            templateText = MessageTemplateEngine.buildFromSms(unknownBody).templateText,
            body = unknownBody,
        )
        assertEquals(MessagePatternStatus.UNKNOWN, saved.definition.status)
        assertTrue(saved.definition.templateText!!.isNotBlank())
    }

    /**
     * A pattern stamped with an older normalization version must NOT
     * participate in runtime matching. The matcher surfaces Unmatched so
     * the user can rebuild.
     */
    @Test
    fun staleNormalizationPatternIsExcludedFromMatching() = runBlocking {
        val repo = buildRepo()
        val body = trainingPosBody
        val saved = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(body),
            status = MessagePatternStatus.APPROVED,
        )
        // Simulate a pattern written by a previous version of the engine.
        val stale = repo.getForSender(1L).single { it.definition.id == saved.definition.id }
            .copy(
                definition = saved.definition.copy(
                    normalizationVersion = NORMALIZATION_VERSION - 1,
                ),
            )
        val outcome = TemplateResolutionService.resolve(
            sender = "BankX",
            body = body,
            smsTimestampMillis = 1L,
            patterns = listOf(stale),
        )
        assertTrue(
            "stale pattern must NOT match — outcome was $outcome",
            outcome is TemplateResolutionResult.Unmatched,
        )
    }

    /**
     * Two structurally identical POS messages with different values must
     * produce one PatternVariant under one PatternFamily — not two duplicate
     * top-level family cards.
     */
    @Test
    fun structurallyIdenticalPosMessagesCollapseIntoOneVariantUnderOneFamily() = runBlocking {
        val repo = buildRepo()
        val pos1 = """
            شراء عبر نقاط البيع
            لدى: KFC
            بمبلغ: 55.00 SAR
            في: 12:30 08-08-2026
            بطاقة مدى رقم: 8219
        """.trimIndent()
        val pos2 = """
            شراء عبر نقاط البيع
            لدى: AMAZON SA
            بمبلغ: 129.50 SAR
            في: 21:15 07-08-2026
            بطاقة مدى رقم: 4444
        """.trimIndent()
        val first = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(pos1, messageCount = 21),
            status = MessagePatternStatus.APPROVED,
        )
        val second = repo.saveDiscovered(
            senderProfileId = 1L,
            discovered = discovered(pos2, messageCount = 6),
            status = MessagePatternStatus.APPROVED,
        )
        assertEquals(first.definition.id, second.definition.id)
        // Same canonical signature → same family row → exactly one family.
        assertEquals(first.family?.id, second.family?.id)
        // Variants in the family list is the original approved template only.
        assertEquals(1, repo.getForSender(1L).size)
    }

    // ----- helpers -----

    private fun discovered(body: String, messageCount: Int = 1): DiscoveredMessagePattern {
        val built = MessageTemplateEngine.buildFromSms(body)
        return DiscoveredMessagePattern(
            signature = SmsStructureNormalizer.signatureFromBody(body),
            friendlyNameHint = built.displayName,
            messageCount = messageCount,
            latestTimestamp = 1L,
            sanitizedSamples = emptyList(),
            suggestedFields = emptyList(),
            looksLikeOtpOrMarketing = false,
            templateText = built.templateText,
            placeholders = built.placeholders,
            transactionTypeName = built.transactionType?.name,
            direction = built.direction,
        )
    }

    private fun buildRepo(): MessagePatternRepository {
        val defs = RoundTripPatternDefinitionDao()
        val fields = RoundTripPatternFieldDao()
        return MessagePatternRepository(
            definitionDao = defs,
            fieldDao = fields,
            database = null,
            senderProfileDao = null,
            familyDao = RoundTripFamilyDao(),
            anchorDao = RoundTripAnchorDao(),
            now = { 1_000L },
        )
    }

    private companion object {
        const val trainingPosBody = """
            شراء عبر نقاط البيع
            لدى: KFC
            بمبلغ: 55.00 SAR
            في: 12:30 08-08-2026
            بطاقة مدى رقم: 8219
        """

        const val equivalentPosBody = """
            شراء عبر نقاط البيع
            لدى: AMAZON SA
            بمبلغ: 129.50 SAR
            في: 21:15 07-08-2026
            بطاقة مدى رقم: 4444
        """
    }
}

/** In-memory DAO stubs that match the production repository contract. */
internal class RoundTripPatternDefinitionDao :
    com.baraa.masroof.data.db.MessagePatternDefinitionDao {
    private val rows = mutableListOf<MessagePatternDefinitionEntity>()
    private var nextId = 1L

    override suspend fun insert(row: MessagePatternDefinitionEntity): Long {
        val id = nextId++
        rows += row.copy(id = id)
        return id
    }

    override suspend fun update(row: MessagePatternDefinitionEntity) {
        val idx = rows.indexOfFirst { it.id == row.id }
        if (idx >= 0) rows[idx] = row
    }

    override suspend fun getById(id: Long): MessagePatternDefinitionEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun getByLineage(lineageId: Long): List<MessagePatternDefinitionEntity> =
        rows.filter { it.lineageId == lineageId || (it.lineageId == 0L && it.id == lineageId) }

    override suspend fun getForSender(senderProfileId: Long): List<MessagePatternDefinitionEntity> =
        rows.filter { it.senderProfileId == senderProfileId }

    override suspend fun getForSenderByStatus(
        senderProfileId: Long,
        status: MessagePatternStatus,
    ): List<MessagePatternDefinitionEntity> =
        rows.filter { it.senderProfileId == senderProfileId && it.status == status }

    override suspend fun findByCanonicalKey(
        senderProfileId: Long,
        canonicalKey: String,
    ): MessagePatternDefinitionEntity? =
        rows.firstOrNull { it.senderProfileId == senderProfileId && it.canonicalKey == canonicalKey }

    override suspend fun findByExactSignature(
        senderProfileId: Long,
        signature: String,
    ): MessagePatternDefinitionEntity? =
        rows.firstOrNull { it.senderProfileId == senderProfileId && it.normalizedSignature == signature }

    override suspend fun getForFamily(familyId: Long): List<MessagePatternDefinitionEntity> =
        rows.filter { it.familyId == familyId }

    override suspend fun getByStatus(status: MessagePatternStatus): List<MessagePatternDefinitionEntity> =
        rows.filter { it.status == status }

    override fun observeUnknown(): Flow<List<MessagePatternDefinitionEntity>> =
        MutableStateFlow(rows.filter { it.status == MessagePatternStatus.UNKNOWN })

    override suspend fun countUnknown(): Int =
        rows.count { it.status == MessagePatternStatus.UNKNOWN }

    override suspend fun getImportable(): List<MessagePatternDefinitionEntity> =
        rows.filter {
            it.status == MessagePatternStatus.APPROVED &&
                it.isActive &&
                it.deprecatedAt == null
        }

    override suspend fun getEffectiveForSender(senderProfileId: Long): List<MessagePatternDefinitionEntity> =
        rows.filter {
            it.senderProfileId == senderProfileId &&
                it.status == MessagePatternStatus.APPROVED &&
                it.isActive &&
                it.deprecatedAt == null
        }

    override suspend fun getSignatureOnly(): List<MessagePatternDefinitionEntity> =
        rows.filter { it.templateText.isNullOrBlank() }

    override suspend fun getEffectiveForSenderAtVersion(
        senderProfileId: Long,
        version: Int,
    ): List<MessagePatternDefinitionEntity> =
        rows.filter {
            it.senderProfileId == senderProfileId &&
                it.status == MessagePatternStatus.APPROVED &&
                it.isActive &&
                it.deprecatedAt == null &&
                it.normalizationVersion == version
        }

    override suspend fun getImportableAtVersion(
        version: Int,
    ): List<MessagePatternDefinitionEntity> =
        rows.filter {
            it.status == MessagePatternStatus.APPROVED &&
                it.isActive &&
                it.deprecatedAt == null &&
                it.normalizationVersion == version
        }
}

internal class RoundTripPatternFieldDao : PatternFieldDefinitionDao {
    private val rows = mutableListOf<PatternFieldDefinitionEntity>()
    private var nextId = 1L

    override suspend fun insert(row: PatternFieldDefinitionEntity): Long {
        val id = nextId++
        rows += row.copy(id = id)
        return id
    }

    override suspend fun insertAll(rows: List<PatternFieldDefinitionEntity>) {
        rows.forEach { insert(it) }
    }

    override suspend fun getForPattern(patternId: Long): List<PatternFieldDefinitionEntity> =
        rows.filter { it.patternId == patternId }

    override suspend fun deleteForPattern(patternId: Long) {
        rows.removeAll { it.patternId == patternId }
    }
}

internal class RoundTripFamilyDao : com.baraa.masroof.data.db.MessagePatternFamilyDao {
    private val rows = mutableListOf<MessagePatternFamilyEntity>()
    private var nextId = 1L

    override suspend fun insert(row: MessagePatternFamilyEntity): Long {
        val existing = rows.firstOrNull {
            it.senderProfileId == row.senderProfileId &&
                it.stableKey == row.stableKey
        }
        if (existing != null) return existing.id
        val id = nextId++
        rows += row.copy(id = id)
        return id
    }

    override suspend fun update(row: MessagePatternFamilyEntity) {
        val idx = rows.indexOfFirst { it.id == row.id }
        if (idx >= 0) rows[idx] = row
    }

    override suspend fun getById(id: Long): MessagePatternFamilyEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun getForSender(senderProfileId: Long): List<MessagePatternFamilyEntity> =
        rows.filter { it.senderProfileId == senderProfileId }

    override suspend fun findByStableKey(
        senderProfileId: Long,
        stableKey: String,
    ): MessagePatternFamilyEntity? =
        rows.firstOrNull { it.senderProfileId == senderProfileId && it.stableKey == stableKey }
}

internal class RoundTripAnchorDao : PatternVariantAnchorDao {
    private val rows = mutableListOf<PatternVariantAnchorEntity>()
    private var nextId = 1L

    override suspend fun insertAll(rows: List<PatternVariantAnchorEntity>) {
        rows.forEach {
            if (this.rows.none { a -> a.variantId == it.variantId && a.normalizedAnchor == it.normalizedAnchor }) {
                this.rows += it.copy(id = nextId++)
            }
        }
    }

    override suspend fun getForVariant(variantId: Long): List<PatternVariantAnchorEntity> =
        rows.filter { it.variantId == variantId }

    override suspend fun deleteForVariant(variantId: Long) {
        rows.removeAll { it.variantId == variantId }
    }
}