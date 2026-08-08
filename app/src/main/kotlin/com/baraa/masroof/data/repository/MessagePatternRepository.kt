package com.baraa.masroof.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.baraa.masroof.data.db.MessagePatternDefinitionDao
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternFamilyDao
import com.baraa.masroof.data.db.MessagePatternFamilyEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternVariantAnchorDao
import com.baraa.masroof.data.db.PatternVariantAnchorEntity
import com.baraa.masroof.data.db.PatternFieldDefinitionDao
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternOrigin
import com.baraa.masroof.data.db.SenderProfileDao
import com.baraa.masroof.sms.CanonicalMessageNormalizer
import com.baraa.masroof.sms.DiscoveredMessagePattern
import com.baraa.masroof.sms.MessageTemplateEngine
import com.baraa.masroof.sms.NORMALIZATION_VERSION
import com.baraa.masroof.sms.PatternDiscoveryService
import com.baraa.masroof.sms.PatternStructure
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsStructureNormalizer
import com.baraa.masroof.sms.SuggestedPatternField
import com.baraa.masroof.transaction.LineBasedFieldParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MessagePattern(
    /** One exact PatternVariant; retained name preserves call-site compatibility. */
    val definition: MessagePatternDefinitionEntity,
    val fields: List<PatternFieldDefinitionEntity>,
    val anchors: List<PatternVariantAnchorEntity> = emptyList(),
    val family: MessagePatternFamilyEntity? = null,
)

class MessagePatternRepository(
    private val definitionDao: MessagePatternDefinitionDao,
    private val fieldDao: PatternFieldDefinitionDao,
    private val database: RoomDatabase? = null,
    private val senderProfileDao: SenderProfileDao? = null,
    private val familyDao: MessagePatternFamilyDao? = null,
    private val anchorDao: PatternVariantAnchorDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val unknownMutex = Mutex()
    constructor(
        definitionDao: MessagePatternDefinitionDao,
        fieldDao: PatternFieldDefinitionDao,
        now: () -> Long,
    ) : this(definitionDao, fieldDao, null, null, null, null, now)

    suspend fun getForSender(senderProfileId: Long): List<MessagePattern> = withContext(Dispatchers.IO) {
        toPatterns(definitionDao.getForSender(senderProfileId))
    }

    suspend fun getById(patternId: Long): MessagePattern? = withContext(Dispatchers.IO) {
        definitionDao.getById(patternId)?.let { toPattern(it) }
    }

    suspend fun getByStatus(status: MessagePatternStatus): List<MessagePattern> =
        withContext(Dispatchers.IO) { toPatterns(definitionDao.getByStatus(status)) }

    suspend fun getImportableForSender(senderProfileId: Long): List<MessagePattern> =
        withContext(Dispatchers.IO) {
            toPatterns(
                definitionDao.getForSender(senderProfileId)
                    .filter(com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible),
            )
        }

    suspend fun getImportable(): List<MessagePattern> = withContext(Dispatchers.IO) {
        toPatterns(definitionDao.getImportable())
    }

    fun observeUnknown(): Flow<List<MessagePatternDefinitionEntity>> = definitionDao.observeUnknown()

    suspend fun countUnknown(): Int = withContext(Dispatchers.IO) { definitionDao.countUnknown() }

    /** Profiles that have at least one APPROVED pattern (eligible as account pattern sources). */
    suspend fun senderProfileIdsWithApprovedPatterns(): Set<Long> = withContext(Dispatchers.IO) {
        definitionDao.getByStatus(MessagePatternStatus.APPROVED)
            .map { it.senderProfileId }
            .toSet()
    }

    /** Count exact structural-template matches. Signature-only rows are not importable. */
    fun countMatches(pattern: MessagePattern, messages: List<com.baraa.masroof.sms.SmsMessage>): Int {
        var n = 0
        for (sms in messages) {
            val body = sms.body ?: continue
            val template = pattern.definition.templateText
            val hit = !template.isNullOrBlank() &&
                com.baraa.masroof.sms.MessageTemplateEngine.matches(template, body)
            if (hit) n++
        }
        return n
    }

    fun countMatchesForTemplate(templateText: String, messages: List<com.baraa.masroof.sms.SmsMessage>): Int =
        messages.count { sms ->
            val body = sms.body
            !body.isNullOrBlank() && com.baraa.masroof.sms.MessageTemplateEngine.matches(templateText, body)
        }

    /** Legacy rows which cannot participate in production template matching. */
    suspend fun getSignatureOnlyForSender(senderProfileId: Long): List<MessagePattern> =
        withContext(Dispatchers.IO) {
            val senderRows = definitionDao.getForSender(senderProfileId)
            val convertedLineages = senderRows
                .filter { !it.templateText.isNullOrBlank() }
                .map { it.lineageId.takeIf { id -> id > 0L } ?: it.id }
                .toSet()
            definitionDao.getSignatureOnly()
                .filter {
                    it.senderProfileId == senderProfileId &&
                        (it.lineageId.takeIf { id -> id > 0L } ?: it.id) !in convertedLineages
                }
                .map { MessagePattern(it, fieldDao.getForPattern(it.id)) }
        }

    sealed class SignatureOnlyConversionResult {
        data object NotFound : SignatureOnlyConversionResult()
        data object SignatureMismatch : SignatureOnlyConversionResult()
        data object NoCanonicalType : SignatureOnlyConversionResult()
        data class Failure(val messageAr: String) : SignatureOnlyConversionResult()
        data class Success(val pattern: MessagePattern) : SignatureOnlyConversionResult()
    }

    /**
     * Explicitly converts one legacy signature-only row using an inbox body held by the caller.
     *
     * The legacy signature is used only as an identity confirmation. The body is neither stored
     * nor logged; the resulting revision contains only a canonical template and label definitions.
     * The legacy row and its fields remain untouched.
     */
    suspend fun convertSignatureOnly(
        patternId: Long,
        inboxBody: String,
    ): SignatureOnlyConversionResult = withContext(Dispatchers.IO) {
        try {
            inTransaction {
                val legacy = definitionDao.getById(patternId)
                    ?.takeIf { it.templateText.isNullOrBlank() }
                    ?: return@inTransaction SignatureOnlyConversionResult.NotFound
                val lineageId = legacy.lineageId.takeIf { it > 0L } ?: legacy.id
                if (definitionDao.getByLineage(lineageId).any { !it.templateText.isNullOrBlank() }) {
                    return@inTransaction SignatureOnlyConversionResult.NotFound
                }
                if (inboxBody.isBlank() ||
                    legacy.normalizedSignature !=
                    com.baraa.masroof.sms.SmsStructureNormalizer.signatureFromBody(inboxBody)
                ) {
                    return@inTransaction SignatureOnlyConversionResult.SignatureMismatch
                }

                val built = MessageTemplateEngine.buildFromSms(inboxBody)
                val canonicalType = built.transactionType
                    ?: return@inTransaction SignatureOnlyConversionResult.NoCanonicalType
                if (built.templateText.isBlank()) {
                    return@inTransaction SignatureOnlyConversionResult.Failure(
                        "تعذر إنشاء قالب بنيوي من الرسالة",
                    )
                }
                val labels = LineBasedFieldParser.splitLines(inboxBody).map { it.label }
                val fields = PatternDiscoveryService.suggestFields(labels)
                val nextVersion = (definitionDao.getByLineage(lineageId).maxOfOrNull { it.version }
                    ?: legacy.version) + 1
                val ts = now()
                val canonicalKey = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                    built.templateText,
                    built.signature,
                    canonicalType.name,
                )
                val newId = definitionDao.insert(
                    legacy.copy(
                        id = 0L,
                        normalizedSignature = revisionSignature(
                            legacy.normalizedSignature,
                            lineageId,
                            nextVersion,
                        ),
                        canonicalKey = canonicalKey,
                        lineageId = lineageId,
                        templateText = built.templateText,
                        transactionType = canonicalType.name,
                        direction = built.direction,
                        channel = built.channel,
                        status = MessagePatternStatus.UNKNOWN,
                        version = nextVersion,
                        isActive = false,
                        origin = PatternOrigin.MIGRATED,
                        confidence = 40,
                        userConfirmed = false,
                        activeFrom = null,
                        deprecatedAt = null,
                        createdAt = ts,
                        updatedAt = ts,
                    ),
                )
                persistFields(newId, fields, built.placeholders)
                SignatureOnlyConversionResult.Success(
                    MessagePattern(definitionDao.getById(newId)!!, fieldDao.getForPattern(newId)),
                )
            }
        } catch (_: Exception) {
            SignatureOnlyConversionResult.Failure("تعذر تحويل القالب القديم")
        }
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
        replaceExampleCount: Boolean = false,
    ): MessagePattern = withContext(Dispatchers.IO) {
        if (discovered.exactVariants.isNotEmpty()) {
            val saved = discovered.exactVariants.map { variant ->
                saveDiscovered(
                    senderProfileId = senderProfileId,
                    discovered = variant.copy(familyKey = discovered.familyKey),
                    status = status,
                    userFriendlyName = userFriendlyName ?: discovered.friendlyNameHint,
                    fields = variant.suggestedFields,
                    origin = origin,
                    userConfirmed = userConfirmed,
                    replaceExampleCount = replaceExampleCount,
                )
            }
            return@withContext saved.firstOrNull()
                ?: error("Semantic discovery contained no exact variants")
        }
        // Canonical signature is recomputed through the production normalizer so
        // training and import share one deterministic identity source.
        val canonicalSignature = SmsStructureNormalizer.signatureFromTemplate(discovered.templateText)
            .takeIf { it.isNotBlank() }
            ?: discovered.signature
        val canonicalKey = canonicalSignature
        val familyStableKey = discovered.familyKey.ifBlank {
            val structure = CanonicalMessageNormalizer.normalizeTemplate(discovered.templateText)
            val type = com.baraa.masroof.transaction.TransactionTypeTaxonomy
                .parse(discovered.transactionTypeName)
            PatternStructure.familyKey(structure, type, discovered.channel)
        }
        val familyId = getOrCreateFamily(
            senderProfileId = senderProfileId,
            stableKey = familyStableKey,
            displayName = userFriendlyName ?: discovered.friendlyNameHint,
            status = status,
        )
        val existing = definitionDao.findByCanonicalKey(senderProfileId, canonicalKey)
        val ts = now()
        if (existing == null) {
            val id = definitionDao.insert(
                MessagePatternDefinitionEntity(
                    senderProfileId = senderProfileId,
                    familyId = familyId,
                    userFriendlyName = userFriendlyName ?: discovered.friendlyNameHint,
                    normalizedSignature = canonicalSignature,
                    canonicalKey = canonicalKey,
                    templateText = discovered.templateText.takeIf { it.isNotBlank() },
                    transactionType = discovered.transactionTypeName,
                    direction = discovered.direction,
                    channel = discovered.channel,
                    status = status,
                    isActive = status == MessagePatternStatus.APPROVED,
                    version = 1,
                    origin = origin,
                    normalizationVersion = NORMALIZATION_VERSION,
                    confidence = if (userConfirmed) {
                        100
                    } else {
                        com.baraa.masroof.transaction.TransactionTypeTaxonomy
                            .discoveryConfidence(discovered.messageCount)
                    },
                    userConfirmed = userConfirmed,
                    exampleCount = discovered.messageCount,
                    activeFrom = if (status == MessagePatternStatus.APPROVED) ts else null,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
            val inserted = definitionDao.getById(id)!!
            if (inserted.lineageId == 0L) {
                definitionDao.update(inserted.copy(lineageId = id))
            }
            persistFields(id, fields, discovered.placeholders)
            persistAnchors(id, discovered.templateText)
            toPattern(definitionDao.getById(id)!!)
        } else {
            // Explicit user decisions win: never downgrade a confirmed status
            // via a non-confirmed (e.g. UNKNOWN) save.
            val effectiveStatus = when {
                existing.userConfirmed && existing.status == MessagePatternStatus.IGNORED ->
                    MessagePatternStatus.IGNORED
                !userConfirmed && existing.userConfirmed -> existing.status
                else -> status
            }
            val updated = existing.copy(
                familyId = existing.familyId ?: familyId,
                userFriendlyName = userFriendlyName ?: existing.userFriendlyName,
                canonicalKey = canonicalKey,
                normalizedSignature = canonicalSignature,
                normalizationVersion = NORMALIZATION_VERSION,
                templateText = existing.templateText ?: discovered.templateText.takeIf { it.isNotBlank() },
                transactionType = discovered.transactionTypeName ?: existing.transactionType,
                direction = discovered.direction ?: existing.direction,
                channel = discovered.channel ?: existing.channel,
                status = effectiveStatus,
                isActive = effectiveStatus == MessagePatternStatus.APPROVED,
                userConfirmed = userConfirmed || existing.userConfirmed,
                exampleCount = if (replaceExampleCount) {
                    discovered.messageCount
                } else {
                    existing.exampleCount + discovered.messageCount
                },
                activeFrom = when {
                    effectiveStatus == MessagePatternStatus.APPROVED && existing.activeFrom == null -> ts
                    else -> existing.activeFrom
                },
                deprecatedAt = when (effectiveStatus) {
                    MessagePatternStatus.APPROVED -> null
                    MessagePatternStatus.DEPRECATED -> existing.deprecatedAt ?: ts
                    else -> existing.deprecatedAt
                },
                updatedAt = ts,
                confidence = if (userConfirmed) 100 else existing.confidence,
            )
            definitionDao.update(updated)
            if (fieldDao.getForPattern(existing.id).isEmpty() && fields.isNotEmpty()) {
                persistFields(existing.id, fields, discovered.placeholders)
            }
            if (anchorDao?.getForVariant(existing.id).isNullOrEmpty()) {
                persistAnchors(existing.id, updated.templateText)
            }
            toPattern(updated)
        }
    }

    suspend fun setStatus(patternId: Long, status: MessagePatternStatus) = withContext(Dispatchers.IO) {
        val existing = definitionDao.getById(patternId) ?: return@withContext
        val ts = now()
        val familyVariants = existing.familyId?.let { definitionDao.getForFamily(it) }
            .orEmpty()
            .ifEmpty { listOf(existing) }
        for (variant in familyVariants) {
            definitionDao.update(
                variant.copy(
                status = status,
                isActive = status == MessagePatternStatus.APPROVED,
                userConfirmed = true,
                activeFrom = if (status == MessagePatternStatus.APPROVED) {
                    variant.activeFrom ?: ts
                } else {
                    variant.activeFrom
                },
                deprecatedAt = when (status) {
                    MessagePatternStatus.APPROVED -> null
                    MessagePatternStatus.DEPRECATED -> ts
                    else -> variant.deprecatedAt
                },
                updatedAt = ts,
                confidence = 100,
            ),
            )
        }
        existing.familyId?.let { refreshFamilyStatus(it) }
        // Approving consolidates equivalent UNKNOWN candidates for the same family.
        if (status == MessagePatternStatus.APPROVED && existing.canonicalKey.isNotBlank()) {
            val siblings = definitionDao.getForSender(existing.senderProfileId).filter {
                it.id != existing.id &&
                    (
                        (existing.familyId != null && it.familyId == existing.familyId) ||
                            it.canonicalKey == existing.canonicalKey
                        ) &&
                    it.status == MessagePatternStatus.UNKNOWN &&
                    it.deprecatedAt == null
            }
            for (sibling in siblings) {
                definitionDao.update(
                    sibling.copy(
                        status = MessagePatternStatus.DEPRECATED,
                        isActive = false,
                        deprecatedAt = ts,
                        updatedAt = ts,
                        userConfirmed = true,
                    ),
                )
            }
        }
    }

    /**
     * Approve a candidate as a production template. Persists APPROVED + active,
     * consolidates duplicate UNKNOWN siblings, returns the approved row.
     */
    suspend fun approveCandidate(patternId: Long): MessagePattern? = withContext(Dispatchers.IO) {
        setStatus(patternId, MessagePatternStatus.APPROVED)
        getById(patternId)
    }

    suspend fun replaceFields(patternId: Long, fields: List<SuggestedPatternField>) =
        withContext(Dispatchers.IO) {
            fieldDao.deleteForPattern(patternId)
            persistFields(patternId, fields)
        }

    sealed class TemplateUpdateResult {
        data class Success(val pattern: MessagePattern) : TemplateUpdateResult()
        data class ValidationError(val messageAr: String) : TemplateUpdateResult()
        data object NotFound : TemplateUpdateResult()
        data object SenderNotFound : TemplateUpdateResult()
        data object SenderInactive : TemplateUpdateResult()
        data class CanonicalCollision(val conflictingPatternId: Long) : TemplateUpdateResult()
        data class Failure(val messageAr: String) : TemplateUpdateResult()
    }

    /**
     * Apply an interactive template edit. Bumps [MessagePatternDefinitionEntity.version]
     * and [MessagePatternDefinitionEntity.updatedAt]. Affects future matching only —
     * never rewrites posted journals or historical transaction rows.
     */
    suspend fun updateTemplate(draft: com.baraa.masroof.sms.TemplateEditDraft): TemplateUpdateResult =
        withContext(Dispatchers.IO) {
            val validation = com.baraa.masroof.sms.TemplateEditValidator.validate(draft)
            if (validation is com.baraa.masroof.sms.TemplateEditValidation.Error) {
                return@withContext TemplateUpdateResult.ValidationError(validation.messageAr)
            }
            try {
                inTransaction {
                    val existing = definitionDao.getById(draft.patternId)
                        ?: return@inTransaction TemplateUpdateResult.NotFound
                    val sender = senderProfileDao?.getById(draft.senderProfileId)
                    if (senderProfileDao != null && sender == null) {
                        return@inTransaction TemplateUpdateResult.SenderNotFound
                    }
                    if (sender != null && !sender.active) {
                        return@inTransaction TemplateUpdateResult.SenderInactive
                    }

                    val lineageId = existing.lineageId.takeIf { it > 0L } ?: existing.id
                    val canonicalKey = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                        draft.templateText.trim(),
                        existing.normalizedSignature,
                        draft.transactionType.name,
                    )
                    val semantic = com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                        draft.templateText.trim(),
                        draft.transactionType.name,
                    )
                    val semanticKey =
                        (semantic as? com.baraa.masroof.sms.SemanticSchemaResult.Safe)?.key
                            ?: if (familyDao == null) {
                                "legacy-edit:${existing.familyId ?: existing.id}"
                            } else {
                                return@inTransaction TemplateUpdateResult.ValidationError(
                                    "تعذر تحديد الهوية المالية للقالب بشكل آمن",
                                )
                            }
                    val targetFamilyId = getOrCreateFamily(
                        senderProfileId = draft.senderProfileId,
                        stableKey = semanticKey,
                        displayName = draft.displayName.trim(),
                        status = draft.status,
                    ) ?: existing.familyId
                    val collision = definitionDao.getForSender(draft.senderProfileId).firstOrNull {
                        it.canonicalKey == canonicalKey &&
                            (it.lineageId.takeIf { id -> id > 0L } ?: it.id) != lineageId
                    }
                    if (collision != null) {
                        return@inTransaction TemplateUpdateResult.CanonicalCollision(collision.id)
                    }

                    val ts = now()
                    val nextVersion = (definitionDao.getByLineage(lineageId).maxOfOrNull { it.version }
                        ?: existing.version) + 1
                    definitionDao.update(existing.copy(deprecatedAt = ts, updatedAt = ts))
                    val newId = definitionDao.insert(
                        existing.copy(
                            id = 0L,
                            senderProfileId = draft.senderProfileId,
                            familyId = targetFamilyId,
                            userFriendlyName = draft.displayName.trim(),
                            normalizedSignature = revisionSignature(
                                existing.normalizedSignature,
                                lineageId,
                                nextVersion,
                            ),
                            canonicalKey = canonicalKey,
                            lineageId = lineageId,
                            templateText = draft.templateText.trim(),
                            transactionType = draft.transactionType.name,
                            direction = com.baraa.masroof.transaction.TransactionTypeTaxonomy
                                .directionStorageName(draft.direction),
                            status = draft.status,
                            version = nextVersion,
                            isActive = draft.active,
                            userConfirmed = true,
                            activeFrom = ts,
                            deprecatedAt = null,
                            createdAt = ts,
                            updatedAt = ts,
                            confidence = 100,
                        ),
                    )
                    persistDraftFields(newId, draft.fields)
                    TemplateUpdateResult.Success(
                        MessagePattern(definitionDao.getById(newId)!!, fieldDao.getForPattern(newId)),
                    )
                }
            } catch (e: Exception) {
                TemplateUpdateResult.Failure(e.message ?: "تعذر حفظ إصدار القالب")
            }
        }

    /**
     * Safely reclassify a mis-approved non-financial pattern without deleting SMS.
     */
    suspend fun reclassifyAsNonFinancial(patternId: Long, reasonAr: String? = null): MessagePattern? =
        withContext(Dispatchers.IO) {
            inTransaction {
                val existing = definitionDao.getById(patternId) ?: return@inTransaction null
                val oldFields = fieldDao.getForPattern(existing.id)
                val lineageId = existing.lineageId.takeIf { it > 0L } ?: existing.id
                val nextVersion = (definitionDao.getByLineage(lineageId).maxOfOrNull { it.version }
                    ?: existing.version) + 1
                val ts = now()
                val targetFamilyId = getOrCreateFamily(
                    senderProfileId = existing.senderProfileId,
                    stableKey = "review:non-financial:$lineageId",
                    displayName = existing.userFriendlyName,
                    status = MessagePatternStatus.UNKNOWN,
                ) ?: existing.familyId
                definitionDao.update(existing.copy(deprecatedAt = ts, updatedAt = ts))
                val newId = definitionDao.insert(
                    existing.copy(
                        id = 0L,
                        familyId = targetFamilyId,
                        normalizedSignature = revisionSignature(
                            existing.normalizedSignature,
                            lineageId,
                            nextVersion,
                        ),
                        lineageId = lineageId,
                        canonicalKey = com.baraa.masroof.sms.TemplateCanonicalizer.canonicalKey(
                            existing.templateText,
                            existing.normalizedSignature,
                            com.baraa.masroof.transaction.TransactionType.NON_FINANCIAL.name,
                        ),
                        transactionType = com.baraa.masroof.transaction.TransactionType.NON_FINANCIAL.name,
                        direction = com.baraa.masroof.transaction.MoneyFlowDirection.NONE.name,
                        status = MessagePatternStatus.UNKNOWN,
                        isActive = false,
                        userConfirmed = false,
                        version = nextVersion,
                        activeFrom = ts,
                        deprecatedAt = null,
                        createdAt = ts,
                        updatedAt = ts,
                        confidence = 40,
                    ),
                )
                if (oldFields.isNotEmpty()) {
                    fieldDao.insertAll(oldFields.map { it.copy(id = 0L, patternId = newId) })
                }
                MessagePattern(definitionDao.getById(newId)!!, fieldDao.getForPattern(newId))
            }
        }

    /**
     * Ensure an UNKNOWN definition exists for an unmatched message (never silent drop).
     *
     * Never creates a new row when the message is already covered:
     *  1. canonical-template identity match (formatting-only variants collapse)
     *  2. the [body] matches an existing saved template of any status
     * In all covered cases the existing pattern's count/metadata is bumped instead.
     */
    /**
     * Rebuild all APPROVED patterns for one sender under the current
     * canonical normalizer. Stale rows (normalizationVersion older than
     * the current [NORMALIZATION_VERSION]) are marked DEPRECATED so the
     * matcher excludes them, then fresh variants are re-derived from the
     * sender's recent inbox SMS and saved with the current version stamp.
     *
     * No financial records — transactions, journals, postings — are
     * touched. Patterns are interpretation configuration.
     */
    suspend fun rebuildForSender(senderProfileId: Long, messages: List<SmsMessage>) =
        withContext(Dispatchers.IO) {
            inTransaction {
                val ts = now()
                val existing = definitionDao.getForSender(senderProfileId)
                // Preserve incompatible rows for audit while excluding them from matching.
                for (def in existing) {
                    if (
                        com.baraa.masroof.sms.PatternRuntimeEligibility.evaluate(def) ==
                        com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION
                    ) {
                        definitionDao.update(
                            def.copy(
                                status = MessagePatternStatus.DEPRECATED,
                                isActive = false,
                                deprecatedAt = def.deprecatedAt ?: ts,
                                updatedAt = ts,
                            ),
                        )
                    }
                }
                val clusters = PatternDiscoveryService.discover(messages, existing)
                    .filterNot {
                        it.looksLikeOtpOrMarketing ||
                            it.looksLikeNonFinancial ||
                            it.familyKey.startsWith("review:")
                    }
                for (cluster in clusters) {
                    saveDiscovered(
                        senderProfileId,
                        cluster,
                        MessagePatternStatus.APPROVED,
                        replaceExampleCount = true,
                    )
                }
                RebuildSummary(
                    rebuiltVariants = clusters.sumOf {
                        it.exactVariants.ifEmpty { listOf(it) }.size
                    },
                    staleDeprecated = existing.count {
                        com.baraa.masroof.sms.PatternRuntimeEligibility.evaluate(it) ==
                            com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION
                    },
                )
            }
        }

    data class RebuildSummary(val rebuiltVariants: Int, val staleDeprecated: Int)

    data class StalePatternRepairResult(
        val rebuildAttempted: Boolean,
        val rebuildSucceeded: Boolean,
        val staleApprovedPatterns: Int,
        val rebuiltVariants: Int,
        val patternsAfterReload: List<MessagePattern>,
    )

    /**
     * Refresh only stale APPROVED semantics represented by this scan.
     * Unobserved families and all financial records remain untouched.
     */
    suspend fun rebuildStaleForSender(
        senderProfileId: Long,
        messages: List<SmsMessage>,
    ): StalePatternRepairResult = withContext(Dispatchers.IO) {
        inTransaction {
            val existing = definitionDao.getForSender(senderProfileId)
            val staleApproved = existing.filter {
                com.baraa.masroof.sms.PatternRuntimeEligibility.evaluate(it) ==
                    com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION
            }
            if (staleApproved.isEmpty()) {
                return@inTransaction StalePatternRepairResult(
                    rebuildAttempted = false,
                    rebuildSucceeded = true,
                    staleApprovedPatterns = 0,
                    rebuiltVariants = 0,
                    patternsAfterReload = toPatterns(existing),
                )
            }

            val staleSemanticKeys = staleApproved.mapNotNull { definition ->
                (
                    com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                        definition.templateText,
                        definition.transactionType,
                    ) as? com.baraa.masroof.sms.SemanticSchemaResult.Safe
                    )?.key
            }.toSet()
            val repairable = PatternDiscoveryService.discover(messages, existing)
                .filter {
                    !it.looksLikeOtpOrMarketing &&
                        !it.looksLikeNonFinancial &&
                        it.familyKey in staleSemanticKeys
                }
            for (cluster in repairable) {
                saveDiscovered(
                    senderProfileId = senderProfileId,
                    discovered = cluster,
                    status = MessagePatternStatus.APPROVED,
                    replaceExampleCount = true,
                )
            }
            val repairedTargetKeys = repairable.map { it.familyKey }.toSet()
            val ts = now()
            staleApproved.forEach { stale ->
                val staleKey = (
                    com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                        stale.templateText,
                        stale.transactionType,
                    ) as? com.baraa.masroof.sms.SemanticSchemaResult.Safe
                    )?.key
                val currentRow = definitionDao.getById(stale.id) ?: return@forEach
                if (
                    staleKey in repairedTargetKeys &&
                    com.baraa.masroof.sms.PatternRuntimeEligibility.evaluate(currentRow) ==
                    com.baraa.masroof.sms.PatternRuntimeEligibilityResult.STALE_NORMALIZATION
                ) {
                    definitionDao.update(
                        currentRow.copy(
                            status = MessagePatternStatus.DEPRECATED,
                            isActive = false,
                            deprecatedAt = currentRow.deprecatedAt ?: ts,
                            updatedAt = ts,
                        ),
                    )
                }
            }

            val reloaded = definitionDao.getForSender(senderProfileId)
            val repairedKeys = reloaded
                .filter(com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible)
                .mapNotNull { definition ->
                    (
                        com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                            definition.templateText,
                            definition.transactionType,
                        ) as? com.baraa.masroof.sms.SemanticSchemaResult.Safe
                        )?.key
                }
                .toSet()
            StalePatternRepairResult(
                rebuildAttempted = true,
                rebuildSucceeded = staleSemanticKeys.intersect(
                    repairable.map { it.familyKey }.toSet(),
                ).all { it in repairedKeys } && repairable.isNotEmpty(),
                staleApprovedPatterns = staleApproved.size,
                rebuiltVariants = repairable.sumOf {
                    it.exactVariants.ifEmpty { listOf(it) }.size
                },
                patternsAfterReload = toPatterns(reloaded),
            )
        }
    }

    suspend fun ensureUnknown(
        senderProfileId: Long,
        signature: String,
        friendlyName: String,
        templateText: String? = null,
        body: String? = null,
    ): MessagePattern = withContext(Dispatchers.IO) {
        unknownMutex.withLock {
            inTransaction {
                ensureUnknownLocked(senderProfileId, signature, friendlyName, templateText, body)
            }
        }
    }

    private suspend fun ensureUnknownLocked(
        senderProfileId: Long,
        signature: String,
        friendlyName: String,
        templateText: String?,
        body: String?,
    ): MessagePattern {
        val built = MessageTemplateEngine.buildFromSms(body ?: templateText)
        val effectiveTemplate = templateText ?: built.templateText.takeIf { it.isNotBlank() }
        val semanticResult = if (!body.isNullOrBlank()) {
            com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromBody(body)
        } else {
            com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                effectiveTemplate,
                built.transactionType?.name,
            )
        }
        val semanticKey = (semanticResult as? com.baraa.masroof.sms.SemanticSchemaResult.Safe)?.key
        if (semanticKey != null) {
            val semanticApproved = definitionDao.getForSender(senderProfileId).filter { candidate ->
                com.baraa.masroof.sms.PatternRuntimeEligibility.isEligible(candidate) &&
                    (
                        com.baraa.masroof.sms.SemanticPatternSchemaNormalizer.fromTemplate(
                            candidate.templateText,
                            candidate.transactionType,
                        ) as? com.baraa.masroof.sms.SemanticSchemaResult.Safe
                        )?.key == semanticKey
            }.groupBy { it.familyId ?: -it.id }
            if (semanticApproved.size == 1) {
                val approved = semanticApproved.values.single().maxBy { it.version }
                val updated = approved.copy(
                    exampleCount = approved.exampleCount + 1,
                    updatedAt = now(),
                )
                definitionDao.update(updated)
                return toPattern(updated)
            }
            val semanticFamily = familyDao?.findByStableKey(senderProfileId, semanticKey)
            if (semanticFamily != null) {
                val candidate = definitionDao.getForFamily(semanticFamily.id)
                    .filter { it.status == MessagePatternStatus.UNKNOWN && it.deprecatedAt == null }
                    .maxByOrNull { it.updatedAt }
                if (candidate != null) {
                    val updated = candidate.copy(
                        exampleCount = candidate.exampleCount + 1,
                        updatedAt = now(),
                    )
                    definitionDao.update(updated)
                    return toPattern(updated)
                }
            }
        }
        // Re-derive canonical signature through the production normalizer so
        // the unknown candidate matches the deterministic identity of any
        // existing approved pattern that was trained on the same structure.
        val canonicalSignature = SmsStructureNormalizer.signatureFromBody(body)
            .takeIf { it.isNotBlank() }
            ?: SmsStructureNormalizer.signatureFromTemplate(effectiveTemplate)
                .takeIf { it.isNotBlank() }
            ?: signature
        val canonicalKey = canonicalSignature
        val existing = definitionDao.findByCanonicalKey(senderProfileId, canonicalKey)
            ?: definitionDao.findByExactSignature(senderProfileId, canonicalSignature)
        if (existing != null) {
            val updated = existing.copy(
                templateText = existing.templateText?.takeIf { it.isNotBlank() } ?: templateText,
                exampleCount = existing.exampleCount + 1,
                normalizedSignature = canonicalSignature,
                canonicalKey = canonicalKey,
                updatedAt = now(),
            )
            definitionDao.update(updated)
            return toPattern(updated)
        }
        val ts = now()
        val familyStableKey = semanticKey ?: "review:${canonicalSignature}"
        val familyId = getOrCreateFamily(
            senderProfileId,
            familyStableKey,
            friendlyName,
            MessagePatternStatus.UNKNOWN,
        )
        val id = definitionDao.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = senderProfileId,
                familyId = familyId,
                userFriendlyName = friendlyName,
                normalizedSignature = canonicalSignature,
                canonicalKey = canonicalKey,
                templateText = effectiveTemplate,
                status = MessagePatternStatus.UNKNOWN,
                version = 1,
                origin = PatternOrigin.USER_TRAINED,
                normalizationVersion = NORMALIZATION_VERSION,
                confidence = 20,
                userConfirmed = false,
                exampleCount = 1,
                createdAt = ts,
                updatedAt = ts,
            ),
        )
        val inserted = definitionDao.getById(id)!!
        if (inserted.lineageId == 0L) {
            definitionDao.update(inserted.copy(lineageId = id))
        }
        persistFields(
            id,
            PatternDiscoveryService.suggestFields(
                com.baraa.masroof.transaction.LineBasedFieldParser.splitLines(body.orEmpty()).map { it.label },
            ),
            built.placeholders,
        )
        persistAnchors(id, effectiveTemplate)
        return toPattern(definitionDao.getById(id)!!)
    }

    /** Any-status template match: covers UNKNOWN rows the importable matcher skips. */
    private suspend fun findByTemplateMatch(
        senderProfileId: Long,
        body: String?,
    ): MessagePatternDefinitionEntity? {
        if (body.isNullOrBlank()) return null
        return definitionDao.getForSender(senderProfileId).firstOrNull { def ->
            !def.templateText.isNullOrBlank() &&
                com.baraa.masroof.sms.MessageTemplateEngine.matches(def.templateText, body)
        }
    }

    private suspend fun persistFields(
        patternId: Long,
        fields: List<SuggestedPatternField>,
        availableTokens: List<String> = emptyList(),
    ) {
        if (fields.isEmpty()) return
        fieldDao.insertAll(
            fields.map { f ->
                PatternFieldDefinitionEntity(
                    patternId = patternId,
                    canonicalField = f.canonicalField,
                    placeholderToken = placeholderTokenFor(f, availableTokens),
                    sourceLabel = f.sourceLabel,
                    extractionStrategy = f.extractionStrategy,
                    required = f.required,
                    role = f.role,
                    valueType = f.valueType,
                )
            },
        )
    }

    private fun placeholderTokenFor(
        field: SuggestedPatternField,
        availableTokens: List<String>,
    ): String {
        val expected = com.baraa.masroof.sms.TemplateResolutionService
            .defaultPlaceholder(field.canonicalField)
        if (expected in availableTokens) return expected
        val generic = when (field.canonicalField) {
            com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_ACCOUNT_LAST4,
            com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_ACCOUNT_LAST4,
            -> "ACCOUNT_LAST4"
            com.baraa.masroof.data.db.PatternCanonicalField.SOURCE_IBAN_LAST4,
            com.baraa.masroof.data.db.PatternCanonicalField.DESTINATION_IBAN_LAST4,
            -> "IBAN_LAST4"
            else -> expected
        }
        return generic.takeIf { it in availableTokens } ?: expected
    }

    private suspend fun persistDraftFields(
        patternId: Long,
        fields: List<com.baraa.masroof.sms.TemplateFieldDraft>,
    ) {
        if (fields.isEmpty()) return
        fieldDao.insertAll(
            fields.map { field ->
                PatternFieldDefinitionEntity(
                    patternId = patternId,
                    canonicalField = field.canonicalField,
                    placeholderToken = field.placeholderToken.trim(),
                    sourceLabel = field.sourceLabel.trim(),
                    required = field.required,
                    role = field.role,
                    valueType = field.valueType,
                )
            },
        )
    }

    private suspend fun getOrCreateFamily(
        senderProfileId: Long,
        stableKey: String,
        displayName: String,
        status: MessagePatternStatus,
    ): Long? {
        val dao = familyDao ?: return null
        val key = stableKey.ifBlank { "legacy:${PatternStructure.normalizeAnchor(displayName)}" }
        val existing = dao.findByStableKey(senderProfileId, key)
        if (existing != null) {
            if (status == MessagePatternStatus.APPROVED && existing.status != MessagePatternStatus.APPROVED) {
                dao.update(existing.copy(status = MessagePatternStatus.APPROVED, updatedAt = now()))
            }
            return existing.id
        }
        val timestamp = now()
        return dao.insert(
            MessagePatternFamilyEntity(
                senderProfileId = senderProfileId,
                stableKey = key,
                displayName = displayName.ifBlank { "نمط رسالة" },
                status = status,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    private suspend fun refreshFamilyStatus(familyId: Long) {
        val family = familyDao?.getById(familyId) ?: return
        val variants = definitionDao.getForFamily(familyId)
        val status = when {
            variants.any(com.baraa.masroof.sms.PatternRuntimeEligibility::isEligible) ->
                MessagePatternStatus.APPROVED
            variants.any { it.status == MessagePatternStatus.UNKNOWN } -> MessagePatternStatus.UNKNOWN
            variants.any { it.status == MessagePatternStatus.IGNORED } -> MessagePatternStatus.IGNORED
            else -> MessagePatternStatus.DEPRECATED
        }
        if (status != family.status) familyDao.update(family.copy(status = status, updatedAt = now()))
    }

    private suspend fun persistAnchors(variantId: Long, templateText: String?) {
        val dao = anchorDao ?: return
        val anchors = PatternStructure.anchorsFromTemplate(templateText)
            .map { (anchor, required) ->
                PatternVariantAnchorEntity(
                    variantId = variantId,
                    normalizedAnchor = anchor,
                    required = required,
                )
            }
        if (anchors.isNotEmpty()) dao.insertAll(anchors)
    }

    private suspend fun toPatterns(definitions: List<MessagePatternDefinitionEntity>): List<MessagePattern> {
        val patterns = ArrayList<MessagePattern>(definitions.size)
        for (definition in definitions) patterns += toPattern(definition)
        return patterns
    }

    private suspend fun toPattern(definition: MessagePatternDefinitionEntity): MessagePattern =
        MessagePattern(
            definition = definition,
            fields = fieldDao.getForPattern(definition.id),
            anchors = anchorDao?.getForVariant(definition.id).orEmpty(),
            family = definition.familyId?.let { familyDao?.getById(it) },
        )

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        database?.withTransaction { block() } ?: block()

    private fun revisionSignature(base: String, lineageId: Long, version: Int): String =
        "${base.substringBefore("#revision:")}#revision:$lineageId:$version"
}
