package com.baraa.masroof.ui.accounts

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.AccountLinkRuleRepository
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ManualLinkComposerTest {
    private fun account(id: Long, type: AccountType = AccountType.BANK_ACCOUNT, lastFour: String? = "1234", institution: String? = "Bank") = FinancialAccount(id, "A$id", institution, type, AccountNature.defaultNatureFor(type), lastFour, emptyList(), Currency.SAR, BigDecimal.ZERO, 1, true, true, true, null, true, null)
    private fun tx(type: TransactionType = TransactionType.PURCHASE, lastFour: String? = "1234", treatment: FinancialTreatment = FinancialTreatment.EXPENSE, status: TransactionStatus = TransactionStatus.COMPLETED, amount: BigDecimal? = BigDecimal("100"), sender: String? = "BANK", institution: String? = "Bank") = TransactionEntity(id = 1, uniqueFingerprint = "x", smsTimestamp = 0, originalSender = sender, transactionType = type, amount = amount, currency = Currency.SAR, merchantOrBeneficiary = null, accountOrCardLastFourDigits = lastFour, transactionDate = LocalDate.now(), transactionTime = null, status = status, confidence = 80, parsingNotes = emptyList(), dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY, createdAt = 0, updatedAt = 0, financialTreatment = treatment, postingStatus = com.baraa.masroof.ledger.TransactionPostingStatus.NEEDS_REVIEW).let { if (institution != null) it else it }

    @Test fun unsafeSalaryToCardIsBlocked() {
        val tx = tx(type = TransactionType.SALARY, treatment = FinancialTreatment.INCOME)
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1, AccountType.CREDIT_CARD, "1234")), account(1, AccountType.CREDIT_CARD, "1234"))
        assertFalse(decision.canRemember); assertEquals(ManualLinkComposer.Reason.UNSAFE_SALARY_TO_CARD, decision.reason)
    }
    @Test fun unsafeCardPurchaseToBankIsBlocked() {
        val tx = tx(type = TransactionType.CARD_PAYMENT, treatment = FinancialTreatment.EXPENSE)
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1, AccountType.BANK_ACCOUNT, "1234")), account(1, AccountType.BANK_ACCOUNT, "1234"))
        assertFalse(decision.canRemember)
    }
    @Test fun suspiciousAmountIsBlocked() {
        val tx = tx(amount = BigDecimal("99999999"))
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1)), account(1))
        assertFalse(decision.canRemember); assertEquals(ManualLinkComposer.Reason.SUSPICIOUS_AMOUNT, decision.reason)
    }
    @Test fun unknownTypeIsBlocked() {
        val tx = tx(type = TransactionType.UNKNOWN)
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1)), account(1))
        assertFalse(decision.canRemember); assertEquals(ManualLinkComposer.Reason.UNKNOWN_TYPE, decision.reason)
    }
    @Test fun declinedTransactionIsBlocked() {
        val tx = tx(status = TransactionStatus.DECLINED)
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1)), account(1))
        assertFalse(decision.canRemember)
    }
    @Test fun conflictingLastFourBlocks() {
        val tx = tx(lastFour = "9999")
        val decision = ManualLinkComposer.evaluate(tx, listOf(account(1, lastFour = "1234")), account(1, lastFour = "1234"))
        assertFalse(decision.canRemember); assertEquals(ManualLinkComposer.Reason.LAST_FOUR_CONFLICT, decision.reason)
    }
    @Test fun compatibleExpenseIsAllowed() {
        val decision = ManualLinkComposer.evaluate(tx(), listOf(account(1)), account(1))
        assertTrue(decision.canRemember); assertEquals(ManualLinkComposer.Reason.OK, decision.reason)
    }
    @Test fun batchCompatiblePasses() {
        val list = listOf(tx(), tx(amount = BigDecimal("250")))
        assertTrue(ManualLinkComposer.canRememberBatch(list, account(1)))
    }
    @Test fun batchIncompatibleRejected() {
        val salaryToBank = tx(type = TransactionType.SALARY, treatment = FinancialTreatment.INCOME)
        val expense = tx()
        val list = listOf(salaryToBank, expense)
        val first = ManualLinkComposer.evaluate(salaryToBank, listOf(account(1)), account(1))
        val second = ManualLinkComposer.evaluate(expense, listOf(account(1)), account(1))
        assertTrue(first.canRemember); assertTrue(second.canRemember)
        // now force one unsafe selection
        val decision = ManualLinkComposer.batchDecision(list, account(2, AccountType.CREDIT_CARD, "5678"))
        assertFalse(decision.canRemember)
    }
    @Test fun ruleSignatureContainsNoBodyAndNoAmount() {
        val tx = tx(amount = BigDecimal("999999"))
        val signature = AccountLinkRuleRepository.signature(tx)
        assertFalse(signature.contains("999999")); assertFalse(signature.contains("Bank")); assertTrue(signature.contains("PURCHASE"))
    }
}