package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.MessagePatternDefinitionDao
import com.baraa.masroof.data.db.MessagePatternDefinitionEntity
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.db.PatternFieldDefinitionDao
import com.baraa.masroof.data.db.PatternFieldDefinitionEntity
import com.baraa.masroof.data.db.PatternCanonicalField
import com.baraa.masroof.data.db.PatternFieldRole
import com.baraa.masroof.data.db.PatternValueType
import com.baraa.masroof.sms.DiscoveredMessagePattern
import com.baraa.masroof.sms.MessageTemplateEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePatternDefinitionDao : MessagePatternDefinitionDao {
    val rows = mutableListOf<MessagePatternDefinitionEntity>()
    private var nextId = 1L

    override suspend fun insert(row: MessagePatternDefinitionEntity): Long {
        val dup = rows.any {
            it.senderProfileId == row.senderProfileId &&
                it.canonicalKey == row.canonicalKey &&
                it.version == row.version
        }
        check(!dup) { "UNIQUE constraint failed: (senderProfileId, canonicalKey)" }
        val id = nextId++
        // Test fixtures default to the current normalization version so they
        // participate in matching without each test having to stamp it.
        val versioned = if (row.normalizationVersion == 0) {
            row.copy(id = id, normalizationVersion = com.baraa.masroof.sms.NORMALIZATION_VERSION)
        } else row.copy(id = id)
        rows += versioned
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
        rows.filter { it.senderProfileId == senderProfileId }.sortedByDescending { it.updatedAt }

    override suspend fun getForSenderByStatus(
        senderProfileId: Long,
        status: MessagePatternStatus,
    ): List<MessagePatternDefinitionEntity> =
        getForSender(senderProfileId).filter { it.status == status }

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
        MutableStateFlow(
            rows.filter {
                it.status == MessagePatternStatus.UNKNOWN && it.deprecatedAt == null
            },
        )

    override suspend fun countUnknown(): Int =
        rows.count { it.status == MessagePatternStatus.UNKNOWN && it.deprecatedAt == null }

    override suspend fun getImportable(): List<MessagePatternDefinitionEntity> =
        rows.filter {
            it.status == MessagePatternStatus.APPROVED && it.isActive && it.deprecatedAt == null
        }

    override suspend fun getEffectiveForSender(
        senderProfileId: Long,
    ): List<MessagePatternDefinitionEntity> =
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

class FakePatternFieldDao : PatternFieldDefinitionDao {
    val rows = mutableListOf<PatternFieldDefinitionEntity>()
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

class MessagePatternRepositoryDedupTest {

    private fun repo(defs: FakePatternDefinitionDao = FakePatternDefinitionDao()): Pair<MessagePatternRepository, FakePatternDefinitionDao> =
        MessagePatternRepository(defs, FakePatternFieldDao()) { 1_000L } to defs

    private fun discovered(body: String, messageCount: Int = 1): DiscoveredMessagePattern {
        val built = MessageTemplateEngine.buildFromSms(body)
        return DiscoveredMessagePattern(
            signature = built.signature,
            friendlyNameHint = built.displayName,
            messageCount = messageCount,
            latestTimestamp = 1L,
            sanitizedSamples = emptyList(),
            suggestedFields = emptyList(),
            looksLikeOtpOrMarketing = false,
            templateText = built.templateText,
            placeholders = built.placeholders,
        )
    }

    private val purchaseBody = """
        شراء عبر الانترنت
        بطاقة: 1234
        مبلغ: 25.50 SAR
        لدى: متجر
    """.trimIndent()

    @Test
    fun sameCanonicalTemplateIsSavedOnlyOnce() = runBlocking {
        val (repo, defs) = repo()
        repo.saveDiscovered(1L, discovered(purchaseBody, messageCount = 3), MessagePatternStatus.APPROVED)
        repo.saveDiscovered(1L, discovered(purchaseBody, messageCount = 2), MessagePatternStatus.APPROVED)
        assertEquals(1, defs.rows.size)
        assertEquals(5, defs.rows.single().exampleCount)
    }

    @Test
    fun formattingOnlyDifferencesDoNotCreateDuplicates() = runBlocking {
        val (repo, defs) = repo()
        val a = discovered(purchaseBody)
        // Same template text, different whitespace/formatting + a different machine signature.
        val b = a.copy(
            signature = "different-machine-signature",
            templateText = a.templateText
                .lines()
                .joinToString("\n\n") { "  ${it.replace(":", " : ")}  " },
        )
        repo.saveDiscovered(1L, a, MessagePatternStatus.APPROVED)
        repo.saveDiscovered(1L, b, MessagePatternStatus.APPROVED)
        assertEquals(1, defs.rows.size)
    }

    @Test
    fun ensureUnknownBumpsCountInsteadOfDuplicating() = runBlocking {
        val (repo, defs) = repo()
        val built = MessageTemplateEngine.buildFromSms(purchaseBody)
        repo.ensureUnknown(1L, built.signature, built.displayName, built.templateText, purchaseBody)
        repo.ensureUnknown(1L, built.signature, built.displayName, built.templateText, purchaseBody)
        assertEquals(1, defs.rows.size)
        assertEquals(2, defs.rows.single().exampleCount)
        assertEquals(MessagePatternStatus.UNKNOWN, defs.rows.single().status)
    }

    @Test
    fun ensureUnknownDoesNotCreateRowWhenBodyMatchesSavedTemplate() = runBlocking {
        val (repo, defs) = repo()
        repo.saveDiscovered(1L, discovered(purchaseBody), MessagePatternStatus.APPROVED)
        assertEquals(1, defs.rows.size)

        // Same structure, different values: an instance of the saved template
        // with a signature the exact lookup would miss.
        val instance = """
            شراء عبر الانترنت
            بطاقة: 9876
            مبلغ: 999.99 SAR
            لدى: مطعم مختلف تماما
        """.trimIndent()
        val built = MessageTemplateEngine.buildFromSms(instance)
        repo.ensureUnknown(1L, "some-drifted-signature", built.displayName, built.templateText, instance)

        assertEquals("must attach to the saved pattern, not create UNKNOWN", 1, defs.rows.size)
        assertEquals(MessagePatternStatus.APPROVED, defs.rows.single().status)
        assertEquals(2, defs.rows.single().exampleCount)
    }

    @Test
    fun explicitIgnoredStatusIsNotDowngradedByNonConfirmedSave() = runBlocking {
        val (repo, defs) = repo()
        repo.saveDiscovered(1L, discovered(purchaseBody), MessagePatternStatus.IGNORED)
        assertTrue(defs.rows.single().userConfirmed)

        repo.saveDiscovered(
            1L,
            discovered(purchaseBody),
            MessagePatternStatus.UNKNOWN,
            userConfirmed = false,
        )
        assertEquals(1, defs.rows.size)
        assertEquals(
            "explicit user ignore must survive a non-confirmed save",
            MessagePatternStatus.IGNORED,
            defs.rows.single().status,
        )
    }

    @Test
    fun differentStructuresRemainSeparatePatterns() = runBlocking {
        val (repo, defs) = repo()
        val transferBody = """
            حوالة صادرة
            من حساب: 1111
            مبلغ: 500.00 SAR
        """.trimIndent()
        repo.saveDiscovered(1L, discovered(purchaseBody), MessagePatternStatus.APPROVED)
        repo.saveDiscovered(1L, discovered(transferBody), MessagePatternStatus.APPROVED)
        assertEquals(2, defs.rows.size)
    }

    @Test
    fun signatureOnlyConversionCreatesInactiveUnknownRevisionAndPreservesLegacyData() = runBlocking {
        val defs = FakePatternDefinitionDao()
        val fields = FakePatternFieldDao()
        val repo = MessagePatternRepository(defs, fields) { 3_000L }
        val signature = MessageTemplateEngine.buildFromSms(purchaseBody).signature
        val legacyId = defs.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = 1L,
                userFriendlyName = "قالب قديم",
                normalizedSignature = signature,
                templateText = null,
                status = MessagePatternStatus.APPROVED,
                isActive = false,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        fields.insert(
            PatternFieldDefinitionEntity(
                patternId = legacyId,
                canonicalField = PatternCanonicalField.MERCHANT,
                sourceLabel = "حقل قديم",
                valueType = PatternValueType.TEXT,
            ),
        )
        val legacyBefore = defs.getById(legacyId)
        val oldFieldsBefore = fields.getForPattern(legacyId)

        val result = repo.convertSignatureOnly(legacyId, purchaseBody)

        assertTrue(result is MessagePatternRepository.SignatureOnlyConversionResult.Success)
        val converted =
            (result as MessagePatternRepository.SignatureOnlyConversionResult.Success).pattern
        assertEquals(legacyBefore, defs.getById(legacyId))
        assertEquals(oldFieldsBefore, fields.getForPattern(legacyId))
        assertEquals(MessagePatternStatus.UNKNOWN, converted.definition.status)
        assertEquals(false, converted.definition.isActive)
        assertEquals(legacyId, converted.definition.lineageId)
        assertEquals(2, converted.definition.version)
        assertTrue(converted.definition.templateText!!.contains("{AMOUNT}"))
        assertTrue(converted.fields.any { it.canonicalField == PatternCanonicalField.TRANSACTION_AMOUNT })
        assertTrue(repo.getSignatureOnlyForSender(1L).isEmpty())
    }

    @Test
    fun signatureOnlyConversionRejectsNonMatchingInboxBody() = runBlocking {
        val defs = FakePatternDefinitionDao()
        val repo = MessagePatternRepository(defs, FakePatternFieldDao()) { 3_000L }
        val legacyId = defs.insert(
            MessagePatternDefinitionEntity(
                senderProfileId = 1L,
                userFriendlyName = "قالب قديم",
                normalizedSignature = "different-signature",
                templateText = null,
                status = MessagePatternStatus.UNKNOWN,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val result = repo.convertSignatureOnly(legacyId, purchaseBody)

        assertTrue(result is MessagePatternRepository.SignatureOnlyConversionResult.SignatureMismatch)
        assertEquals(1, defs.rows.size)
    }

    @Test
    fun updateTemplateCreatesImmutableRevisionAndPreservesOldFields() = runBlocking {
        val defs = FakePatternDefinitionDao()
        val fieldDao = FakePatternFieldDao()
        val repo = MessagePatternRepository(defs, fieldDao) { 1_000L }
        val saved = repo.saveDiscovered(1L, discovered(purchaseBody), MessagePatternStatus.APPROVED)
        fieldDao.insert(
            PatternFieldDefinitionEntity(
                patternId = saved.definition.id,
                canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                placeholderToken = "AMOUNT",
                sourceLabel = "مبلغ",
                role = PatternFieldRole.PRIMARY,
                valueType = PatternValueType.MONEY,
                required = true,
            ),
        )
        val oldFields = fieldDao.getForPattern(saved.definition.id)
        val beforeVersion = saved.definition.version
        val draft = com.baraa.masroof.sms.TemplateEditDraft(
            patternId = saved.definition.id,
            senderProfileId = 1L,
            displayName = "شراء معدّل",
            transactionType = com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE,
            direction = com.baraa.masroof.transaction.MoneyFlowDirection.OUTFLOW,
            templateText = "مبلغ: {AMOUNT}",
            status = MessagePatternStatus.APPROVED,
            active = true,
            fields = listOf(
                com.baraa.masroof.sms.TemplateFieldDraft(
                    placeholderToken = "AMOUNT",
                    canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                    sourceLabel = "القيمة",
                    role = PatternFieldRole.PRIMARY,
                    valueType = PatternValueType.MONEY,
                    required = true,
                ),
            ),
        )
        val result = repo.updateTemplate(draft)
        assertTrue(result is MessagePatternRepository.TemplateUpdateResult.Success)
        val updated = (result as MessagePatternRepository.TemplateUpdateResult.Success).pattern
        assertEquals(beforeVersion + 1, updated.definition.version)
        assertEquals("شراء معدّل", updated.definition.userFriendlyName)
        assertEquals(
            com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE.name,
            updated.definition.transactionType,
        )
        assertEquals(2, defs.rows.size)
        assertEquals(1_000L, defs.rows.first { it.id == saved.definition.id }.deprecatedAt)
        assertEquals(oldFields, fieldDao.getForPattern(saved.definition.id))
        assertEquals("القيمة", fieldDao.getForPattern(updated.definition.id).single().sourceLabel)
    }

    @Test
    fun updateTemplateRejectsFinancialApproveWithoutAmount() = runBlocking {
        val (repo, _) = repo()
        val saved = repo.saveDiscovered(1L, discovered(purchaseBody), MessagePatternStatus.APPROVED)
        val draft = com.baraa.masroof.sms.TemplateEditDraft(
            patternId = saved.definition.id,
            senderProfileId = 1L,
            displayName = "حد يومي",
            transactionType = com.baraa.masroof.transaction.TransactionType.ONLINE_PURCHASE,
            direction = com.baraa.masroof.transaction.MoneyFlowDirection.OUTFLOW,
            templateText = "تم تغيير الحد اليومي للشراء عبر الانترنت",
            status = MessagePatternStatus.APPROVED,
            active = true,
            fields = emptyList(),
        )
        val result = repo.updateTemplate(draft)
        assertTrue(result is MessagePatternRepository.TemplateUpdateResult.ValidationError)
    }

    @Test
    fun explicitNonFinancialReclassificationCreatesInactiveRevision() = runBlocking {
        val defs = FakePatternDefinitionDao()
        val fields = FakePatternFieldDao()
        val repo = MessagePatternRepository(defs, fields) { 2_000L }
        val saved = repo.saveDiscovered(
            1L,
            discovered(purchaseBody),
            MessagePatternStatus.APPROVED,
        )
        fields.insert(
            PatternFieldDefinitionEntity(
                patternId = saved.definition.id,
                canonicalField = PatternCanonicalField.TRANSACTION_AMOUNT,
                placeholderToken = "AMOUNT",
                sourceLabel = "مبلغ",
                valueType = PatternValueType.MONEY,
            ),
        )

        val revised = repo.reclassifyAsNonFinancial(saved.definition.id, "اقتراح فقط")

        assertEquals(2, defs.rows.size)
        assertEquals(2_000L, defs.rows.first { it.id == saved.definition.id }.deprecatedAt)
        assertEquals(MessagePatternStatus.APPROVED, defs.rows.first { it.id == saved.definition.id }.status)
        assertEquals(MessagePatternStatus.UNKNOWN, revised!!.definition.status)
        assertEquals(false, revised.definition.isActive)
        assertEquals(
            fields.getForPattern(saved.definition.id).map { it.canonicalField },
            revised.fields.map { it.canonicalField },
        )
    }
}
