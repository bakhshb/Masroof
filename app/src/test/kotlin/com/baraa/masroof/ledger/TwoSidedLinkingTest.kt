package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.DateSource
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TwoSidedLinkingTest {
    private val generator = JournalGenerationService(systemAccounts = { 1000L + it.ordinal })

    @Test
    fun creditCardPaymentJournalNeedsBothSides() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT, "راتب")
        val card = account(2, AccountType.CREDIT_CARD, "فيزا")
        val tx = baseTx(FinancialTreatment.CREDIT_CARD_PAYMENT)
        assertNull(generator.generate(tx, bank, null))
        assertNull(generator.generate(tx, null, card))
        val draft = generator.generate(tx, bank, card)
        assertNotNull(draft)
        assertEquals(JournalType.CREDIT_CARD_PAYMENT, draft!!.journalType)
        assertEquals(2, draft.postings.size)
        assertEquals(card.id, draft.postings.first { it.postingSide == PostingSide.DEBIT }.accountId)
        assertEquals(bank.id, draft.postings.first { it.postingSide == PostingSide.CREDIT }.accountId)
    }

    @Test
    fun internalTransferIsNetWorthNeutralDraft() = runBlocking {
        val a = account(1, AccountType.BANK_ACCOUNT, "A")
        val b = account(3, AccountType.BANK_ACCOUNT, "B")
        val draft = generator.generate(baseTx(FinancialTreatment.INTERNAL_TRANSFER), a, b)
        assertNotNull(draft)
        assertEquals(JournalType.INTERNAL_TRANSFER, draft!!.journalType)
        val debit = draft.postings.single { it.postingSide == PostingSide.DEBIT }.amount
        val credit = draft.postings.single { it.postingSide == PostingSide.CREDIT }.amount
        assertEquals(0, debit.compareTo(credit))
    }

    @Test
    fun twoSidedTreatmentsFlagRequiresTwoAccounts() {
        assertTrue(FinancialTreatment.CREDIT_CARD_PAYMENT.requiresTwoAccounts)
        assertTrue(FinancialTreatment.INTERNAL_TRANSFER.requiresTwoAccounts)
        assertTrue(FinancialTreatment.INVESTMENT.requiresTwoAccounts)
        assertTrue(!FinancialTreatment.EXPENSE.requiresTwoAccounts)
    }

    private fun account(id: Long, type: AccountType, name: String) = FinancialAccount(
        id = id,
        displayName = name,
        institutionName = "Bank",
        accountType = type,
        accountNature = AccountNature.defaultNatureFor(type),
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = type != AccountType.CREDIT_CARD,
        isOwnedByUser = true,
        isActive = true,
        notes = null,
    )

    private fun baseTx(treatment: FinancialTreatment) = TransactionEntity(
        id = 10,
        uniqueFingerprint = "fp",
        smsTimestamp = 1L,
        originalSender = "bank",
        transactionType = TransactionType.CARD_PAYMENT,
        amount = BigDecimal("200.00"),
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = null,
        transactionDate = LocalDate.of(2026, 8, 3),
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 90,
        parsingNotes = emptyList(),
        dateSource = DateSource.FROM_BODY,
        createdAt = 0,
        updatedAt = 0,
        financialTreatment = treatment,
    )
}
