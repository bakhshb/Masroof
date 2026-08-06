package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.AccountIdentifierDao
import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountDao
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

enum class IdentifierAddResult { Added, Updated, DisabledDuplicate, Rejected }

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
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll(): Flow<List<AccountIdentifierEntity>> = dao.observeAll()

    fun observeByAccount(accountId: Long): Flow<List<AccountIdentifierEntity>> = dao.observeByAccount(accountId)

    suspend fun getForAccount(accountId: Long): List<AccountIdentifierEntity> = dao.getByAccount(accountId)

    suspend fun getActiveForAccount(accountId: Long): List<AccountIdentifierEntity> = dao.getByAccount(accountId).filter { it.isActive }

    suspend fun findByTypeAndValue(type: AccountIdentifierType, value: String): AccountIdentifierEntity? =
        dao.findByTypeAndValue(type, normalize(type, value))

    suspend fun findByValue(value: String): AccountIdentifierEntity? = dao.findByValue(value)

    suspend fun findAccountsByIdentifier(type: AccountIdentifierType, value: String): List<FinancialAccount> {
        val normalized = normalize(type, value)
        if (normalized.isEmpty()) return emptyList()
        val matches = dao.getByType(type).filter { it.normalizedValue == normalized && it.isActive }
        return matches.mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
    }

    suspend fun accountsForSender(sender: String?): List<FinancialAccount> {
        if (sender.isNullOrBlank()) return emptyList()
        val key = normalize(AccountIdentifierType.SENDER_ALIAS, sender)
        if (key.isEmpty()) return emptyList()
        return dao.getByType(AccountIdentifierType.SENDER_ALIAS)
            .filter { it.isActive && it.normalizedValue == key }
            .mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
    }

    suspend fun addOrUpdate(accountId: Long, form: IdentifierForm): IdentifierAddOutcome {
        val account = accountDao.getById(accountId)?.toDomainRef()
            ?: return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), "حساب غير موجود")
        val typeCompatible = isCompatible(account.accountType, form.identifierType)
        if (!typeCompatible) return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), "نوع المعرف غير مناسب لنوع الحساب")
        val normalized = normalize(form.identifierType, form.rawValue)
        val validation = validate(form.identifierType, normalized)
        if (validation != null) return IdentifierAddOutcome(IdentifierAddResult.Rejected, null, emptyList(), validation)

        val existingForAccount = dao.getByAccount(accountId).firstOrNull { it.identifierType == form.identifierType && it.normalizedValue == normalized }
        if (existingForAccount != null) {
            val updated = existingForAccount.copy(displayLabel = form.displayLabel, isActive = true, updatedAt = now())
            dao.update(updated)
            return IdentifierAddOutcome(IdentifierAddResult.Updated, updated, emptyList(), null)
        }
        val crossAccount = dao.findByValue(normalized)
            ?.takeIf { it.accountId != accountId && it.identifierType == form.identifierType && it.isActive }
            ?.let { accountDao.getById(it.accountId)?.toDomainRef() }
            ?: run {
                val anyOther = dao.getByType(form.identifierType)
                    .firstOrNull { it.normalizedValue == normalized && it.accountId != accountId && it.isActive }
                anyOther?.let { accountDao.getById(it.accountId)?.toDomainRef() }
            }
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
        val inserted = if (newId > 0L) dao.getByAccount(accountId).firstOrNull { it.id == newId } else null
        val conflictList = if (crossAccount != null) listOf(crossAccount) else emptyList()
        val message = if (conflictList.isNotEmpty()) "هذا المعرف مستخدم في حساب آخر، وقد تحتاج العمليات المرتبطة به إلى مراجعة يدوية." else null
        return IdentifierAddOutcome(
            result = if (conflictList.isNotEmpty()) IdentifierAddResult.DisabledDuplicate else IdentifierAddResult.Added,
            identifier = inserted,
            conflictingAccounts = conflictList,
            message = message,
        )
    }

    suspend fun setActive(identifierId: Long, active: Boolean) {
        val items = dao.observeAll().first()
        val target = items.firstOrNull { it.id == identifierId } ?: return
        dao.update(target.copy(isActive = active, updatedAt = now()))
    }

    suspend fun updateDisplayLabel(identifierId: Long, label: String) {
        val items = dao.observeAll().first()
        val target = items.firstOrNull { it.id == identifierId } ?: return
        dao.update(target.copy(displayLabel = label, updatedAt = now()))
    }

    suspend fun delete(identifier: AccountIdentifierEntity) = dao.delete(identifier)

    suspend fun detectConflicts(): Map<String, List<FinancialAccount>> {
        val all = dao.observeAll().first()
        val grouped = all.groupBy { it.identifierType to it.normalizedValue }
        val result = mutableMapOf<String, List<FinancialAccount>>()
        for ((key, entries) in grouped) {
            if (entries.size <= 1) continue
            val accounts = entries.mapNotNull { accountDao.getById(it.accountId)?.toDomainRef() }
            result["${key.first}:${key.second}"] = accounts
        }
        return result
    }

    /** Idempotent backfill: convert legacy lastFourDigits values to typed AccountIdentifier rows. */
    suspend fun backfillFromLegacyLastFour(): Int {
        var inserted = 0
        for (account in accountDao.getActive()) {
            val legacy = account.lastFourDigits ?: continue
            if (legacy.isBlank()) continue
            val type = defaultIdentifierTypeFor(account.accountType) ?: continue
            val normalized = normalize(type, legacy)
            if (normalized.length != 4) continue
            if (dao.findByTypeAndValue(type, normalized) != null) continue
            val id = dao.insert(
                AccountIdentifierEntity(
                    accountId = account.id,
                    identifierType = type,
                    normalizedValue = normalized,
                    displayLabel = legacy,
                    isActive = true,
                    createdAt = now(),
                    updatedAt = now(),
                ),
            )
            if (id > 0L) inserted += 1
        }
        return inserted
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
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'; '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    else -> c
                }
            }.joinToString("")
            val digits = ascii.filter { it.isDigit() }
            return digits.takeLast(4)
        }

        fun validate(type: AccountIdentifierType, normalized: String): String? {
            if (normalized.isEmpty()) return "قيمة المعرف مطلوبة"
            return when (type) {
                AccountIdentifierType.SENDER_ALIAS -> if (normalized.length < 2) "اسم المرسل قصير جدًا" else null
                AccountIdentifierType.ACCOUNT_LAST4,
                AccountIdentifierType.CREDIT_CARD_LAST4,
                AccountIdentifierType.DEBIT_CARD_LAST4,
                AccountIdentifierType.IBAN_LAST4,
                AccountIdentifierType.WALLET_LAST4 -> if (normalized.length != 4) "يجب أن يحتوي المعرف على أربعة أرقام بالضبط" else null
            }
        }

        fun isCompatible(accountType: AccountType, type: AccountIdentifierType): Boolean = when (accountType) {
            AccountType.BANK_ACCOUNT -> type in setOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.DEBIT_CARD_LAST4, AccountIdentifierType.IBAN_LAST4, AccountIdentifierType.SENDER_ALIAS)
            AccountType.CREDIT_CARD -> type in setOf(AccountIdentifierType.CREDIT_CARD_LAST4, AccountIdentifierType.SENDER_ALIAS)
            AccountType.DIGITAL_WALLET, AccountType.WALLET -> type in setOf(AccountIdentifierType.WALLET_LAST4, AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.SENDER_ALIAS)
            AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT -> type in setOf(AccountIdentifierType.ACCOUNT_LAST4, AccountIdentifierType.IBAN_LAST4, AccountIdentifierType.SENDER_ALIAS)
            else -> type == AccountIdentifierType.SENDER_ALIAS
        }

        fun defaultIdentifierTypeFor(accountType: AccountType): AccountIdentifierType? = when (accountType) {
            AccountType.BANK_ACCOUNT, AccountType.INVESTMENT_ACCOUNT, AccountType.SUKUK_ACCOUNT -> AccountIdentifierType.ACCOUNT_LAST4
            AccountType.CREDIT_CARD -> AccountIdentifierType.CREDIT_CARD_LAST4
            AccountType.DIGITAL_WALLET, AccountType.WALLET -> AccountIdentifierType.WALLET_LAST4
            else -> null
        }
    }
}
