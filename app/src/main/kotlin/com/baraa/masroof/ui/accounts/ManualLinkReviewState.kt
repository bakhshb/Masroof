package com.baraa.masroof.ui.accounts

import com.baraa.masroof.data.db.AccountLinkRuleEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class ExistingRuleView(
    val ruleId: Long,
    val targetAccountName: String,
    val expectedAccountType: AccountType,
    val confirmationCount: Int,
    val lastUsedAt: Long,
    val active: Boolean,
)

fun ruleViewOf(rule: AccountLinkRuleEntity, account: FinancialAccount?): ExistingRuleView = ExistingRuleView(
    ruleId = rule.id,
    targetAccountName = account?.displayName ?: "حساب غير متاح",
    expectedAccountType = rule.expectedAccountType,
    confirmationCount = rule.confirmationCount,
    lastUsedAt = rule.lastConfirmedAt,
    active = rule.active,
)

fun formatLastUsed(millis: Long): String {
    val date = Date(millis); val locale = Locale("ar")
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
}