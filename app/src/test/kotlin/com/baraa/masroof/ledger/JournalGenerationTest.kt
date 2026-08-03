package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class JournalGenerationTest {
    private val day = LocalDate.of(2025, 1, 1)
    private fun account(id: Long, type: AccountType, nature: AccountNature) = FinancialAccount(
        id = id, displayName = "A$id", institutionName = null, accountType = type,
        accountNature = nature, lastFourDigits = null, senderAliases = emptyList(), currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO, openingBalanceDate = 0, includeInNetWorth = true,
        includeInLiquidity = type == AccountType.BANK_ACCOUNT, isOwnedByUser = true,
        isActive = true, notes = null,
    )
    private fun tx(treatment: FinancialTreatment, status: TransactionStatus = TransactionStatus.COMPLETED) = TransactionEntity(
        id = 1, uniqueFingerprint = "x", smsTimestamp = 0, originalSender = null,
        transactionType = TransactionType.PURCHASE, amount = BigDecimal("250"), currency = Currency.SAR,
        merchantOrBeneficiary = null, accountOrCardLastFourDigits = null, transactionDate = day,
        transactionTime = null, status = status, confidence = 100, parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY, createdAt = 0, updatedAt = 0,
        financialTreatment = treatment, categorySource = CategorySource.UNCLASSIFIED,
    )
    private val generator = JournalGenerationService(systemAccounts = { 99L })

    @Test fun creditCardPaymentMovesBankToCardWithoutExpenseClearing() = runBlocking {
        val draft = generator.generate(
            tx(FinancialTreatment.CREDIT_CARD_PAYMENT),
            account(1, AccountType.BANK_ACCOUNT, AccountNature.ASSET),
            account(2, AccountType.CREDIT_CARD, AccountNature.LIABILITY),
        )!!
        assertEquals(JournalType.CREDIT_CARD_PAYMENT, draft.journalType)
        assertEquals(listOf(2L, 1L), draft.postings.map { it.accountId })
        assertTrue(JournalValidator.validate(draft.copy(postingStatus = JournalPostingStatus.POSTED), true).valid)
    }

    @Test fun investmentTransferPreservesNetWorthAndFeeUsesExpenseClearing() = runBlocking {
        val transfer = generator.generate(
            tx(FinancialTreatment.INVESTMENT), account(1, AccountType.BANK_ACCOUNT, AccountNature.ASSET),
            account(2, AccountType.INVESTMENT_ACCOUNT, AccountNature.ASSET),
        )!!
        val fee = generator.generate(tx(FinancialTreatment.BANK_FEE), account(1, AccountType.BANK_ACCOUNT, AccountNature.ASSET), null)!!
        assertEquals(JournalType.INVESTMENT_TRANSFER, transfer.journalType)
        assertEquals(JournalType.BANK_FEE, fee.journalType)
        assertEquals(99L, fee.postings.first().accountId)
    }

    @Test fun pendingAndDeclinedTransactionsCreateNoJournal() = runBlocking {
        assertNull(generator.generate(tx(FinancialTreatment.EXPENSE, TransactionStatus.PENDING), account(1, AccountType.BANK_ACCOUNT, AccountNature.ASSET), null))
        assertNull(generator.generate(tx(FinancialTreatment.EXPENSE, TransactionStatus.DECLINED), account(1, AccountType.BANK_ACCOUNT, AccountNature.ASSET), null))
    }
}
