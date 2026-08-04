package com.baraa.masroof.ui.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountLinkRuleRepository
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import java.math.BigDecimal

/** Pure pre-validation so both the UI and batch flows can decide whether to remember a mapping. */
object ManualLinkComposer {
    enum class Reason { OK, UNKNOWN_TYPE, SUSPICIOUS_AMOUNT, LAST_FOUR_CONFLICT, AMBIGUOUS_ACCOUNT, DECLINED_OR_PENDING, INSTITUTION_CONFLICT, UNSAFE_SALARY_TO_CARD, UNSAFE_CARD_PURCHASE_TO_BANK, UNSAFE_BANK_TO_LIABILITY }

    data class Decision(val canRemember: Boolean, val reason: Reason)

    fun evaluate(transaction: TransactionEntity, accounts: List<FinancialAccount>, selected: FinancialAccount): Decision {
        if (transaction.transactionType == TransactionType.UNKNOWN) return Decision(false, Reason.UNKNOWN_TYPE)
        if (transaction.amount == null || transaction.amount.signum() <= 0) return Decision(false, Reason.SUSPICIOUS_AMOUNT)
        if (transaction.amount > BigDecimal("10000000")) return Decision(false, Reason.SUSPICIOUS_AMOUNT)
        if (transaction.status == TransactionStatus.DECLINED || transaction.status == TransactionStatus.PENDING) return Decision(false, Reason.DECLINED_OR_PENDING)
        val lastFour = transaction.accountOrCardLastFourDigits?.takeIf { it.length == 4 }
        if (lastFour != null && selected.lastFourDigits != null && selected.lastFourDigits != lastFour) return Decision(false, Reason.LAST_FOUR_CONFLICT)
        val candidatesWithSameLastFour = if (lastFour != null) accounts.count { it.lastFourDigits == lastFour } else 0
        if (lastFour != null && candidatesWithSameLastFour > 1) return Decision(false, Reason.AMBIGUOUS_ACCOUNT)
        val institution = selected.institutionName?.lowercase()
        val sender = transaction.originalSender?.lowercase().orEmpty()
        if (!institution.isNullOrBlank() && sender.isNotBlank() && !sender.contains(institution) && institution !in sender) {
            val senderMentionsOther = accounts.filter { it.id != selected.id }.any { !it.institutionName.isNullOrBlank() && sender.contains(it.institutionName!!.lowercase()) }
            if (senderMentionsOther) return Decision(false, Reason.INSTITUTION_CONFLICT)
        }
        if (transaction.transactionType.name.contains("SALARY") && selected.accountType != AccountType.BANK_ACCOUNT) return Decision(false, Reason.UNSAFE_SALARY_TO_CARD)
        if (transaction.transactionType == TransactionType.CARD_PAYMENT && selected.accountType == AccountType.BANK_ACCOUNT) return Decision(false, Reason.UNSAFE_CARD_PURCHASE_TO_BANK)
        if (transaction.financialTreatment == FinancialTreatment.EXPENSE && selected.accountType == AccountType.LOAN) return Decision(false, Reason.UNSAFE_BANK_TO_LIABILITY)
        return Decision(true, Reason.OK)
    }

    fun canRememberBatch(transactions: List<TransactionEntity>, selected: FinancialAccount): Boolean = transactions.all { evaluate(it, listOf(selected), selected).canRemember }
    fun batchDecision(transactions: List<TransactionEntity>, selected: FinancialAccount): Decision {
        transactions.forEach { tx ->
            val d = evaluate(tx, listOf(selected), selected); if (!d.canRemember) return Decision(false, d.reason)
        }
        return Decision(true, Reason.OK)
    }

    /** Pure data shape for the manual review UI; never logs SMS body or amount. */
    data class ExistingRule(val accountDisplayName: String, val confirmationCount: Int, val lastConfirmedAt: Long, val ruleId: Long)
}