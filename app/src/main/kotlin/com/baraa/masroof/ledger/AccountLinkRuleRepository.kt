package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountLinkRuleDao
import com.baraa.masroof.data.db.AccountLinkRuleEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountType
import kotlinx.coroutines.flow.Flow

class AccountLinkRuleRepository(private val dao: AccountLinkRuleDao, private val now: () -> Long = { System.currentTimeMillis() }) {
    fun observeAll(): Flow<List<AccountLinkRuleEntity>> = dao.observeAll()
    suspend fun find(transaction: TransactionEntity, accounts: List<FinancialAccount>): FinancialAccount? {
        val rule = dao.bySignature(signature(transaction)) ?: return null
        if (!rule.active || transaction.accountOrCardLastFourDigits != null) return null
        val account = accounts.firstOrNull { it.id == rule.accountId && it.isActive && it.accountType == rule.expectedAccountType } ?: return null
        return if (safe(account.accountType, transaction)) account else null
    }
    suspend fun findRule(transaction: TransactionEntity): AccountLinkRuleEntity? = dao.bySignature(signature(transaction))?.takeIf { it.active }
    suspend fun remember(transaction: TransactionEntity, account: FinancialAccount, direction: String) {
        require(safe(account.accountType, transaction)) { "unsafe_link_rule" }
        val signature = signature(transaction); val old = dao.bySignature(signature); val timestamp = now()
        if (old == null) dao.insert(AccountLinkRuleEntity(signature = signature, senderKey = senderKey(transaction.originalSender), institutionKey = account.institutionName?.lowercase(), parserName = "registry", transactionType = transaction.transactionType, financialTreatment = transaction.financialTreatment, channel = "sms", direction = direction, expectedAccountType = account.accountType, accountId = account.id, confirmationCount = 1, lastConfirmedAt = timestamp, lastUsedAt = timestamp))
        else dao.update(old.copy(accountId = account.id, expectedAccountType = account.accountType, direction = direction, confirmationCount = old.confirmationCount + 1, lastConfirmedAt = timestamp, lastUsedAt = timestamp, active = true))
    }
    suspend fun applyExisting(transaction: TransactionEntity, accounts: List<FinancialAccount>): FinancialAccount? {
        val rule = dao.bySignature(signature(transaction))?.takeIf { it.active } ?: return null
        if (transaction.accountOrCardLastFourDigits != null) return null
        val account = accounts.firstOrNull { it.id == rule.accountId && it.isActive && it.accountType == rule.expectedAccountType } ?: return null
        if (!safe(account.accountType, transaction)) return null
        dao.update(rule.copy(lastUsedAt = now(), confirmationCount = rule.confirmationCount + 1))
        return account
    }

    companion object {
        fun signature(t: TransactionEntity): String = listOf(senderKey(t.originalSender), t.transactionType.name, t.financialTreatment.name).joinToString("|")
        private fun senderKey(sender: String?): String = sender.orEmpty().lowercase().filter { it.isLetterOrDigit() }.take(48)
        private fun safe(type: AccountType, t: TransactionEntity): Boolean = when {
            t.transactionType.name.contains("SALARY") -> type == AccountType.BANK_ACCOUNT
            t.transactionType == com.baraa.masroof.transaction.TransactionType.CARD_PAYMENT -> type == AccountType.CREDIT_CARD
            else -> true
        }
    }
}
