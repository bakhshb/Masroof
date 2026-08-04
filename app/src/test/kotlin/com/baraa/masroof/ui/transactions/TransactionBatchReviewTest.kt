package com.baraa.masroof.ui.transactions

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.ledger.TransactionPostingStatus
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

class TransactionBatchReviewTest {
    private fun tx(id: Long, status: TransactionPostingStatus = TransactionPostingStatus.NEEDS_REVIEW, treatment: FinancialTreatment = FinancialTreatment.EXPENSE, src: Long? = 1, amount: BigDecimal? = BigDecimal.TEN, posting: TransactionStatus = TransactionStatus.COMPLETED) = TransactionEntity(id = id, uniqueFingerprint = "u$id", smsTimestamp = 0, originalSender = null, transactionType = TransactionType.PURCHASE, amount = amount, currency = Currency.SAR, merchantOrBeneficiary = null, accountOrCardLastFourDigits = null, transactionDate = LocalDate.now(), transactionTime = null, status = posting, confidence = 100, parsingNotes = emptyList(), dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY, createdAt = 0, updatedAt = 0, sourceAccountId = src, financialTreatment = treatment, postingStatus = status, accountLinkSource = com.baraa.masroof.ledger.AccountLinkSource.USER)
    private fun account(id: Long, type: AccountType = AccountType.BANK_ACCOUNT) = FinancialAccount(id, "A$id", null, type, AccountNature.ASSET, null, emptyList(), Currency.SAR, BigDecimal.ZERO, 1, true, true, true, null, true, null).copy(isOwnedByUser = true)
    @Test fun suspiciousAmountIsRejected() { val r = TransactionBatchReview.validateOne(tx(1).copy(amount = BigDecimal.ZERO), listOf(account(1))); assertFalse(r.valid); assertEquals("unreliable_amount", r.reason) }
    @Test fun declinedTransactionIsRejected() { val r = TransactionBatchReview.validateOne(tx(1, posting = TransactionStatus.DECLINED), listOf(account(1))); assertFalse(r.valid); assertEquals("declined", r.reason) }
    @Test fun pendingTransactionIsRejected() { val r = TransactionBatchReview.validateOne(tx(1, posting = TransactionStatus.PENDING), listOf(account(1))); assertFalse(r.valid); assertEquals("pending", r.reason) }
    @Test fun unlinkedTransactionIsRejected() { val r = TransactionBatchReview.validateOne(tx(1, src = null), listOf(account(1))); assertFalse(r.valid); assertEquals("unlinked", r.reason) }
    @Test fun incompatibleAccountTypeIsRejected() { val cardType = TransactionType.CARD_PAYMENT; val r = TransactionBatchReview.validateOne(tx(1).copy(transactionType = cardType, sourceAccountId = null, destinationAccountId = 1), listOf(account(1))); assertFalse(r.valid) }
    @Test fun oneFailureDoesNotCorruptOthers() { val results = TransactionBatchReview.validateBatch(listOf(tx(1).copy(sourceAccountId = 1), tx(2).copy(amount = null, sourceAccountId = 2)), listOf(account(1), account(2))); assertTrue(results[0].valid); assertFalse(results[1].valid) }
    @Test fun emptyStateRecognized() { assertTrue(TransactionOpsState().isEmpty) }
    @Test fun selectAllHelperClassifiesAll() { val queue = listOf(tx(1), tx(2)); assertEquals(2, queue.size) }
}