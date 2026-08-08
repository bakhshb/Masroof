package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountLinkRuleDao
import com.baraa.masroof.data.db.AccountLinkRuleEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.Flow

/**
 * Learned account-link rules from user confirmations.
 *
 * Signature includes sender, type, last-4 (when present), treatment, and
 * direction so card SMS can learn and two-sided links keep both sides.
 */
class AccountLinkRuleRepository(
    private val dao: AccountLinkRuleDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeAll(): Flow<List<AccountLinkRuleEntity>> = dao.observeAll()

    suspend fun find(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        direction: String = DIRECTION_SOURCE,
    ): FinancialAccount? {
        val rule = findRule(transaction, direction) ?: return null
        val account = accounts.firstOrNull {
            it.id == rule.accountId && it.isActive && it.accountType == rule.expectedAccountType
        } ?: return null
        return if (safe(account.accountType, transaction)) account else null
    }

    suspend fun findRule(
        transaction: TransactionEntity,
        direction: String = DIRECTION_SOURCE,
    ): AccountLinkRuleEntity? {
        return dao.bySignature(signature(transaction, direction))?.takeIf { it.active }
    }

    suspend fun remember(transaction: TransactionEntity, account: FinancialAccount, direction: String) {
        require(safe(account.accountType, transaction)) { "unsafe_link_rule" }
        val sig = signature(transaction, direction)
        val old = dao.bySignature(sig)
        val timestamp = now()
        if (old == null) {
            dao.insert(
                AccountLinkRuleEntity(
                    signature = sig,
                    senderKey = senderKey(transaction.originalSender),
                    institutionKey = account.institutionName?.lowercase(),
                    parserName = "registry",
                    transactionType = transaction.transactionType,
                    financialTreatment = transaction.financialTreatment,
                    channel = "sms",
                    direction = direction,
                    expectedAccountType = account.accountType,
                    accountId = account.id,
                    confirmationCount = 1,
                    lastConfirmedAt = timestamp,
                    lastUsedAt = timestamp,
                ),
            )
        } else {
            dao.update(
                old.copy(
                    accountId = account.id,
                    expectedAccountType = account.accountType,
                    direction = direction,
                    confirmationCount = old.confirmationCount + 1,
                    lastConfirmedAt = timestamp,
                    lastUsedAt = timestamp,
                    active = true,
                ),
            )
        }
    }

    suspend fun applyExisting(
        transaction: TransactionEntity,
        accounts: List<FinancialAccount>,
        direction: String = DIRECTION_SOURCE,
    ): FinancialAccount? {
        val rule = findRule(transaction, direction) ?: return null
        val account = accounts.firstOrNull {
            it.id == rule.accountId && it.isActive && it.accountType == rule.expectedAccountType
        } ?: return null
        if (!safe(account.accountType, transaction)) return null
        dao.update(rule.copy(lastUsedAt = now(), confirmationCount = rule.confirmationCount + 1))
        return account
    }

    companion object {
        const val DIRECTION_SOURCE: String = "source"
        const val DIRECTION_DESTINATION: String = "destination"

        fun signature(t: TransactionEntity, direction: String = DIRECTION_SOURCE): String =
            listOf(
                senderKey(t.originalSender),
                t.transactionType.name,
                lastFourKey(t.accountOrCardLastFourDigits),
                t.financialTreatment.name,
                direction,
            ).joinToString("|")

        private fun senderKey(sender: String?): String =
            sender.orEmpty().lowercase().filter { it.isLetterOrDigit() }.take(48)

        private fun lastFourKey(raw: String?): String {
            val digits = raw.orEmpty().filter { it.isDigit() }.takeLast(4)
            return if (digits.length == 4) digits else "-"
        }

        private fun safe(type: AccountType, t: TransactionEntity): Boolean = when {
            t.transactionType.name.contains("SALARY") -> type == AccountType.BANK_ACCOUNT
            t.transactionType == com.baraa.masroof.transaction.TransactionType.CARD_PAYMENT ->
                type == AccountType.CREDIT_CARD
            else -> true
        }
    }
}
