package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class AutomaticAccountLinkingEngineTest {
    private fun account(id: Long, type: AccountType, last: String?, alias: String = "bank") = FinancialAccount(id, "A$id", "Bank", type, AccountNature.defaultNatureFor(type), last, listOf(alias), Currency.SAR, BigDecimal.ZERO, 1, true, true, true, null, true, null)
    private fun tx(last: String?, treatment: FinancialTreatment = FinancialTreatment.EXPENSE) = TransactionEntity(1,"x",1,"bank",TransactionType.PURCHASE,BigDecimal.ONE,Currency.SAR,null,last,null,null,TransactionStatus.COMPLETED,100, emptyList(),com.baraa.masroof.data.db.DateSource.FROM_BODY,1,1, financialTreatment=treatment)
    @Test fun senderAliasAndLastFourIsConfirmed() { val m=AccountMatcher.match(tx("1234"), listOf(account(1,AccountType.BANK_ACCOUNT,"1234"))); assertEquals(AccountLinkConfidence.CONFIRMED,m.level); assertFalse(m.needsReview) }
    @Test fun incompatibleAndDuplicateLastFourNeedReview() { assertEquals(AccountLinkConfidence.UNMATCHED,AccountMatcher.match(tx("1234", FinancialTreatment.CREDIT_CARD_PAYMENT),listOf(account(1,AccountType.CASH,"1234"))).level); assertEquals(AccountLinkConfidence.UNMATCHED,AccountMatcher.match(tx("1234"),listOf(account(1,AccountType.BANK_ACCOUNT,"1234"),account(2,AccountType.BANK_ACCOUNT,"1234"))).level) }
    @Test fun singleCompatibleSenderIsHigh() { val m=AccountMatcher.match(tx(null),listOf(account(1,AccountType.BANK_ACCOUNT,null))); assertEquals(AccountLinkConfidence.HIGH,m.level); assertFalse(m.needsReview) }
}
