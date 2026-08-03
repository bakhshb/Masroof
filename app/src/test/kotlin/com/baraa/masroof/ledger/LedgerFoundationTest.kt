package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.JournalEntryEntity
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.data.db.LedgerPostingEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.CategorySource
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LedgerFoundationTest {
    private val day = LocalDate.of(2025, 1, 15)
    private fun account(id: Long, nature: AccountNature = AccountNature.ASSET, type: AccountType = AccountType.BANK_ACCOUNT, opening: String = "1000", liquidity: Boolean = nature == AccountNature.ASSET) = FinancialAccount(
        id = id, displayName = "A$id", institutionName = "Bank$id", accountType = type,
        accountNature = nature, lastFourDigits = "${1000 + id}", senderAliases = emptyList(),
        currency = Currency.SAR, openingBalance = BigDecimal(opening),
        openingBalanceDate = day.atStartOfDay(ZoneId.of("Asia/Riyadh")).toInstant().toEpochMilli(),
        includeInNetWorth = true, includeInLiquidity = liquidity, isOwnedByUser = true,
        isActive = true, notes = null,
    )
    private fun journal(status: JournalPostingStatus = JournalPostingStatus.POSTED, postings: List<LedgerPostingEntity>): JournalWithPostings =
        JournalWithPostings(
            JournalEntryEntity(1, null, JournalType.EXPENSE, status, day, LocalTime.NOON, "test", 0, 0, generatedBy = JournalGeneratedBy.SYSTEM, generationVersion = 1),
            postings,
        )
    private fun posting(accountId: Long, side: PostingSide, amount: String) =
        LedgerPostingEntity(accountId = accountId, journalEntryId = 1, postingSide = side, amount = BigDecimal(amount), currency = Currency.SAR, createdAt = 0)

    @Test fun balancedTwoPostingJournalIsAccepted() {
        val draft = JournalDraft(null, JournalType.EXPENSE, JournalPostingStatus.POSTED, day, descriptionCode = "x", postings = listOf(
            PostingDraft(1, PostingSide.DEBIT, BigDecimal("10"), Currency.SAR),
            PostingDraft(2, PostingSide.CREDIT, BigDecimal("10"), Currency.SAR),
        ))
        assertTrue(JournalValidator.validate(draft, true).valid)
    }
    @Test fun unbalancedAndNonPositivePostedJournalsAreRejected() {
        val unbalanced = JournalDraft(null, JournalType.EXPENSE, JournalPostingStatus.POSTED, day, descriptionCode = "x", postings = listOf(
            PostingDraft(1, PostingSide.DEBIT, BigDecimal("10"), Currency.SAR), PostingDraft(2, PostingSide.CREDIT, BigDecimal("9"), Currency.SAR)))
        val zero = unbalanced.copy(postings = listOf(PostingDraft(1, PostingSide.DEBIT, BigDecimal.ZERO, Currency.SAR)))
        assertFalse(JournalValidator.validate(unbalanced, true).valid)
        assertFalse(JournalValidator.validate(zero, false).valid)
    }
    @Test fun incompleteDraftIsAllowed() {
        val draft = JournalDraft(null, JournalType.UNASSIGNED, JournalPostingStatus.DRAFT, day, descriptionCode = "x", postings = emptyList())
        assertTrue(JournalValidator.validate(draft, false).valid)
        assertFalse(JournalValidator.validate(draft, true).valid)
    }
    @Test fun assetPurchaseAndSalaryUseDebitCreditBalanceRules() {
        val bank = account(1)
        val purchase = journal(postings = listOf(posting(1, PostingSide.CREDIT, "100")))
        val salary = journal(postings = listOf(posting(1, PostingSide.DEBIT, "500")))
        assertEquals(BigDecimal("1400"), AccountBalanceService.balance(bank, listOf(purchase, salary), day))
    }
    @Test fun liabilityPurchasePaymentAndRefundUseCreditIncrease() {
        val card = account(2, AccountNature.LIABILITY, AccountType.CREDIT_CARD, "0", false)
        val purchase = journal(postings = listOf(posting(2, PostingSide.CREDIT, "250")))
        val payment = journal(postings = listOf(posting(2, PostingSide.DEBIT, "100")))
        val refund = journal(postings = listOf(posting(2, PostingSide.DEBIT, "50")))
        assertEquals(BigDecimal("100"), AccountBalanceService.balance(card, listOf(purchase, payment, refund), day))
    }
    @Test fun internalTransferPreservesNetWorthAndCashWithdrawalMovesLiquidity() {
        val bank = account(1, opening = "1000")
        val cash = account(2, type = AccountType.CASH, opening = "0")
        val transfer = journal(postings = listOf(posting(2, PostingSide.DEBIT, "200"), posting(1, PostingSide.CREDIT, "200")))
        val totals = AccountBalanceService.totals(listOf(bank, cash), listOf(transfer), day)
        assertEquals(BigDecimal("1000"), totals.netWorth.getValue(Currency.SAR))
        assertEquals(BigDecimal("1000"), totals.liquidity.getValue(Currency.SAR))
    }
    @Test fun unpostedAndVoidedJournalsDoNotAffectBalance() {
        val bank = account(1)
        val draft = journal(JournalPostingStatus.NEEDS_REVIEW, listOf(posting(1, PostingSide.CREDIT, "100")))
        val voided = journal(JournalPostingStatus.VOIDED, listOf(posting(1, PostingSide.CREDIT, "100")))
        assertEquals(BigDecimal("1000"), AccountBalanceService.balance(bank, listOf(draft, voided), day))
    }
    @Test fun sameDayJournalIsAppliedAfterOpeningAndDateOnlyUsesEndOfDay() {
        val bank = account(1)
        val evening = JournalWithPostings(
            journal(postings = listOf(posting(1, PostingSide.CREDIT, "10"))).journal.copy(effectiveTime = LocalTime.of(20, 0)),
            listOf(posting(1, PostingSide.CREDIT, "10")),
        )
        assertEquals(BigDecimal("1000"), AccountBalanceService.balance(bank, listOf(evening), day, LocalTime.NOON))
        assertEquals(BigDecimal("990"), AccountBalanceService.balance(bank, listOf(evening), day))
    }
    @Test fun ambiguousLastFourNeedsReview() {
        val tx = transaction(lastFour = "1001")
        val a = account(1); val duplicate = account(3).copy(lastFourDigits = "1001")
        val result = AccountMatcher.match(tx, listOf(a, duplicate))
        assertNull(result.account); assertTrue(result.needsReview)
    }
    @Test fun exactLastFourMatchesWithoutStoringFullNumber() {
        val tx = transaction(lastFour = "1001")
        val result = AccountMatcher.match(tx, listOf(account(1)))
        assertEquals(1L, result.account?.id); assertEquals(AccountLinkSource.LAST_FOUR_MATCH, result.source)
    }
    @Test fun foreignCurrenciesAreNotConsolidatedIntoSar() {
        val usd = account(1).copy(currency = Currency.USD)
        val totals = AccountBalanceService.totals(listOf(usd), emptyList(), day)
        assertTrue(totals.consolidatedNetWorthIncomplete)
        assertEquals(BigDecimal("1000"), totals.netWorth.getValue(Currency.USD))
    }
    private fun transaction(lastFour: String) = TransactionEntity(
        id = 1, uniqueFingerprint = "f", smsTimestamp = day.atStartOfDay(ZoneId.of("Asia/Riyadh")).toInstant().toEpochMilli(), originalSender = null,
        transactionType = TransactionType.PURCHASE, amount = BigDecimal("10"), currency = Currency.SAR,
        merchantOrBeneficiary = null, accountOrCardLastFourDigits = lastFour, transactionDate = day,
        transactionTime = null, status = TransactionStatus.COMPLETED, confidence = 100,
        parsingNotes = emptyList(), dateSource = DateSource.FROM_BODY, createdAt = 0, updatedAt = 0,
        financialTreatment = FinancialTreatment.EXPENSE, categorySource = CategorySource.UNCLASSIFIED,
    )
}
