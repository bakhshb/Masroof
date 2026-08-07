package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.AccountIdentifierDao
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.ledger.AccountIdentifierCompatibility
import com.baraa.masroof.rules.AccountIdentifierSnapshot
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.Flow

enum class IdentifierAddResult {
    Added,
    Updated,
    /** Inserted successfully, but the same type+value already exists on another account. */
    AddedWithConflict,
    @Deprecated("Use AddedWithConflict", ReplaceWith("AddedWithConflict"))
    DisabledDuplicate,
    Rejected,
}

data class IdentifierForm(
    val identifierType: AccountIdentifierType,
    val displayLabel: String,
    val rawValue: String,
)

data class IdentifierAddOutcome(
    val result: IdentifierAddResult,
    val identifier: AccountIdentifierEntity?,
    val conflictingAccounts: List<FinancialAccount>,
    val message: String?,
)

private fun FinancialAccountEntity.toDomainRef(): FinancialAccount = toDomain()

class AccountIdentifierRepository(
    private val dao: AccountIdentifierDao,
    private val accountDao: FinancialAccountDao,
    private val senderProfileDao: com.baraa.masroof.data.db.SenderProfileDao? = null,
    private val accountSenderDao: com.baraa.masroof.data.db.AccountSenderProfileDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll(): Flow<List<AccountIdentifierEntity>> = dao.observeAll()

    fun observeByAccount(accountId: Long): Flow<List<AccountIdentifierEntity>> = dao.observeByAccount(accountId)

    suspend fun getForAccount(accountId: Long): List<AccountIdentifierEntity> = dao.getByAccount(accountId)

    suspend fun getActiveForAccount(accountId: Long): List<AccountIdentifierEntity> =
        dao.getByAccount(accountId).filter { it.isActive }

    suspend fun getActiveSnapshots(): List<AccountIdentifierSnapshot> =
        dao.getActive().map {
            AccountIdentifierSnapshot(it.accountId, it.identifierType, it.normalizedValue)
        }

    suspend fun findByTypeAndValue(type: AccountIdentifierType, value: String): AccountIdentifierEntity? =
        dao.findByTypeAndValue(type, normalize(type, value))

    suspend fun findByValue(value: String): AccountIdentifierEntity? = dao.findByValue(value)

    suspend fun findAccountsByIdentifier(type: AccountIdentifierType, value: String): List<FinancialAccount> {
        val normalized = normalize(type, value)
        if (normalized.isEmpty()) return emptyList()
        val matches = dao.getByType(type).filter { it.normalizedValue == normalized && it.isActive }
        return matches.mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
    }

    /**
     * Accounts that have any active last-four identifier equal to [value]
     * (ACCOUNT / DEBIT / CREDIT / IBAN / WALLET). Excludes [SENDER_ALIAS].
     */
    suspend fun findAccountsByLastFourAnyType(value: String): List<FinancialAccount> {
        val normalized = normalize(AccountIdentifierType.ACCOUNT_LAST4, value)
        if (normalized.length != 4) return emptyList()
        val matches = dao.getActive().filter {
            it.identifierType != AccountIdentifierType.SENDER_ALIAS &&
                it.normalizedValue == normalized
        }
        return matches
            .mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
            .distinctBy { it.id }
    }

    suspend fun accountsForSender(sender: String?): List<FinancialAccount> {
        if (sender.isNullOrBlank()) return emptyList()
        val key = normalize(AccountIdentifierType.SENDER_ALIAS, sender)
        if (key.isEmpty()) return emptyList()
        val fromAlias = dao.getByType(AccountIdentifierType.SENDER_ALIAS)
            .filter { it.isActive && it.normalizedValue == key }
            .mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
        // Preferred: SenderProfile cross-ref (many accounts per sender).
        val fromProfile = run {
            val profile = senderProfileDao?.findByKey(key) ?: return@run emptyList()
            accountSenderDao?.accountIdsForSender(profile.id)
                ?.mapNotNull { accountDao.getById(it)?.toDomainRef() }
                .orEmpty()
        }
        return (fromProfile + fromAlias)
            .filter { it.isOwnedByUser && it.isActive && it.systemAccountKey == null }
            .distinctBy { it.id }
    }

    /** Sender keys explicitly attached to active, user-owned accounts (typed only). */
    suspend fun activeOwnedSenderAliases(): Set<String> {
        ensureLegacyIdentifierBackfill()
        val ownedActiveIds = accountDao.getActive()
            .filter { it.isOwnedByUser && it.systemAccountKey == null }
            .map { it.id }
            .toSet()
        return dao.getByType(AccountIdentifierType.SENDER_ALIAS)
            .asSequence()
            .filter { it.isActive && it.accountId in ownedActiveIds }
            .map { normalize(AccountIdentifierType.SENDER_ALIAS, it.normalizedValue) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Legacy column backfill is performed by [com.baraa.masroof.data.db.MasroofDatabase.MIGRATION_14_15].
     * Kept as a no-op so older call sites remain safe and idempotent.
     */
    suspend fun ensureLegacyIdentifierBackfill() {
        // no-op after schema v15
    }

    @Deprecated("Legacy columns removed in schema v15; migration performs backfill")
    suspend fun backfillFromLegacyLastFour(): Int = 0

    @Deprecated("Legacy columns removed in schema v15; migration performs backfill")
    suspend fun backfillFromLegacySenderAliases(): Int = 0

    suspend fun addOrUpdate(accountId: Long, form: IdentifierForm): IdentifierAddOutcome {
        val account = accountDao.getById(accountId)?.toDomainRef()
            ?: return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), "حساب غير موجود")
        if (!isCompatible(account.accountType, form.identifierType)) {
            return IdentifierAddOutcome(
                IdentifierAddResult.Rejected,
                null,
                emptyList(),
                "نوع المعرف غير مناسب لنوع الحساب",
            )
        }
        val normalized = normalize(form.identifierType, form.rawValue)
        val validation = validate(form.identifierType, normalized)
        if (validation != null) {
            return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), validation)
        }

        val existingForAccount = dao.getByAccount(accountId)
            .firstOrNull { it.identifierType == form.identifierType && it.normalizedValue == normalized }
        if (existingForAccount != null) {
            val updated = existingForAccount.copy(
                displayLabel = form.displayLabel,
                isActive = true,
                updatedAt = now(),
            )
            dao.update(updated)
            return IdentifierAddOutcome(IdentifierAddResult.Updated, updated, emptyList(), null)
        }

        val conflicts = dao.getByType(form.identifierType)
            .filter { it.normalizedValue == normalized && it.accountId != accountId && it.isActive }
            .mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }

        val newId = dao.insert(
            AccountIdentifierEntity(
                accountId = accountId,
                identifierType = form.identifierType,
                normalizedValue = normalized,
                displayLabel = form.displayLabel,
                isActive = true,
                createdAt = now(),
                updatedAt = now(),
            ),
        )
        val inserted = if (newId > 0L) dao.getById(newId) else null
        // Dual-write: keep SenderProfile + cross-ref in sync while SENDER_ALIAS is deprecated.
        if (form.identifierType == AccountIdentifierType.SENDER_ALIAS && inserted != null) {
            syncSenderProfileLink(accountId, normalized, form.rawValue.trim().ifBlank { normalized })
        }
        val message = if (conflicts.isNotEmpty()) {
            "هذا المعرف مستخدم في حساب آخر، وقد تحتاج العمليات المرتبطة به إلى مراجعة يدوية."
        } else {
            null
        }
        return IdentifierAddOutcome(
            result = if (conflicts.isNotEmpty()) IdentifierAddResult.AddedWithConflict else IdentifierAddResult.Added,
            identifier = inserted,
            conflictingAccounts = conflicts,
            message = message,
        )
    }

    /**
     * Prefer [SenderProfileRepository.associateAccount] for new code.
     * Kept so legacy SENDER_ALIAS writes also populate the profile model.
     */
    private suspend fun syncSenderProfileLink(accountId: Long, key: String, display: String) {
        val profileDao = senderProfileDao ?: return
        val linkDao = accountSenderDao ?: return
        val ts = now()
        val existing = profileDao.findByKey(key)
        val profileId = if (existing == null) {
            profileDao.insert(
                com.baraa.masroof.data.db.SenderProfileEntity(
                    displaySender = display,
                    normalizedSenderKey = key,
                    active = true,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        } else {
            if (!existing.active) {
                profileDao.update(existing.copy(active = true, updatedAt = ts))
            }
            existing.id
        }
        linkDao.insert(
            com.baraa.masroof.data.db.AccountSenderProfileCrossRef(
                accountId = accountId,
                senderProfileId = profileId,
                createdAt = ts,
            ),
        )
    }

    suspend fun setActive(identifierId: Long, active: Boolean) {
        val target = dao.getById(identifierId) ?: return
        dao.update(target.copy(isActive = active, updatedAt = now()))
    }

    suspend fun updateDisplayLabel(identifierId: Long, label: String) {
        val target = dao.getById(identifierId) ?: return
        dao.update(target.copy(displayLabel = label, updatedAt = now()))
    }

    /**
     * Replace the normalized value of an existing identifier. Validates type
     * rules and reuses [addOrUpdate] conflict detection by deleting then
     * re-inserting under the same account when the value changes.
     */
    suspend fun updateValue(identifierId: Long, rawValue: String, displayLabel: String? = null): IdentifierAddOutcome {
        val existing = dao.getById(identifierId)
            ?: return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), "المعرف غير موجود")
        val form = IdentifierForm(
            identifierType = existing.identifierType,
            displayLabel = displayLabel ?: existing.displayLabel,
            rawValue = rawValue,
        )
        val normalized = normalize(form.identifierType, form.rawValue)
        if (normalized == existing.normalizedValue) {
            displayLabel?.let { updateDisplayLabel(identifierId, it) }
            return IdentifierAddOutcome(IdentifierAddResult.Updated, dao.getById(identifierId), emptyList(), null)
        }
        dao.delete(existing)
        return addOrUpdate(existing.accountId, form)
    }

    suspend fun delete(identifier: AccountIdentifierEntity) = dao.delete(identifier)

    suspend fun detectConflicts(): Map<String, List<FinancialAccount>> {
        val all = dao.getActive()
        val grouped = all.groupBy { it.identifierType to it.normalizedValue }
        val result = mutableMapOf<String, List<FinancialAccount>>()
        for ((key, entries) in grouped) {
            if (entries.map { it.accountId }.distinct().size <= 1) continue
            val accounts = entries.mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
            result["${key.first}:${key.second}"] = accounts
        }
        return result
    }

    companion object {
        /** Normalizes a candidate value for storage/lookup. */
        fun normalize(type: AccountIdentifierType, value: String): String {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return ""
            if (type == AccountIdentifierType.SENDER_ALIAS) {
                return com.baraa.masroof.ledger.FinancialInstitutionResolver.senderKey(trimmed).orEmpty()
            }
            val ascii = trimmed.toCharArray().map { c ->
                when (c) {
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    else -> c
                }
            }.joinToString("")
            val digits = ascii.filter { it.isDigit() }
            return digits.takeLast(4)
        }

        fun validate(type: AccountIdentifierType, normalized: String): String? {
            if (normalized.isEmpty()) return "قيمة المعرف مطلوبة"
            return when (type) {
                AccountIdentifierType.SENDER_ALIAS ->
                    if (normalized.length < 2) "اسم المرسل قصير جدًا" else null
                AccountIdentifierType.ACCOUNT_LAST4,
                AccountIdentifierType.CREDIT_CARD_LAST4,
                AccountIdentifierType.DEBIT_CARD_LAST4,
                AccountIdentifierType.IBAN_LAST4,
                AccountIdentifierType.WALLET_LAST4 ->
                    if (normalized.length != 4) "يجب أن يحتوي المعرف على أربعة أرقام بالضبط" else null
            }
        }

        fun isCompatible(accountType: AccountType, type: AccountIdentifierType): Boolean =
            AccountIdentifierCompatibility.isCompatibleWithAccount(accountType, type)

        fun defaultIdentifierTypeFor(accountType: AccountType): AccountIdentifierType? =
            AccountIdentifierCompatibility.defaultIdentifierTypeFor(accountType)
    }
}
