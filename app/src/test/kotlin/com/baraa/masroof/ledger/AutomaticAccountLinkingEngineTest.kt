package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
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

class AutomaticAccountLinkingEngineTest {
    private fun account(id: Long, type: AccountType) = FinancialAccount(
        id = id,
        displayName = "A$id",
        institutionName = "Bank",
        accountType = type,
        accountNature = AccountNature.defaultNatureFor(type),
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 1,
        includeInNetWorth = true,
        includeInLiquidity = true,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
    )

    private fun tx(
        last: String?,
        treatment: FinancialTreatment = FinancialTreatment.EXPENSE,
    ) = TransactionEntity(
        id = 1,
        uniqueFingerprint = "x",
        smsTimestamp = 1,
        originalSender = "bank",
        transactionType = TransactionType.PURCHASE,
        amount = BigDecimal.ONE,
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = last,
        transactionDate = null,
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 100,
        parsingNotes = emptyList(),
        dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY,
        createdAt = 1,
        updatedAt = 1,
        financialTreatment = treatment,
    )

    private fun repoWith(vararg triples: Triple<FinancialAccount, AccountIdentifierType, String>): AccountIdentifierRepository {
        val accountDao = FakeAccountDao()
        val repo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        for ((account, type, value) in triples) {
            val entity = FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                accountNature = account.accountNature,
                currency = account.currency,
                openingBalance = account.openingBalance,
                openingBalanceDate = account.openingBalanceDate,
                includeInNetWorth = account.includeInNetWorth,
                includeInLiquidity = account.includeInLiquidity,
                isOwnedByUser = account.isOwnedByUser,
                systemAccountKey = account.systemAccountKey,
                isActive = account.isActive,
                notes = account.notes,
                createdAt = 0L,
                updatedAt = 0L,
            )
            runBlocking { accountDao.insert(entity) }
            runBlocking {
                repo.addOrUpdate(account.id, IdentifierForm(type, account.displayName, value))
            }
        }
        return repo
    }

    @Test
    fun creditCardLastFourLinksToCreditCardNotBank() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT)
        val credit = account(2, AccountType.CREDIT_CARD)
        val repo = repoWith(
            Triple(bank, AccountIdentifierType.SENDER_ALIAS, "bank"),
            Triple(credit, AccountIdentifierType.CREDIT_CARD_LAST4, "7271"),
        )
        val m = AccountMatcher.match(tx("7271", FinancialTreatment.EXPENSE), listOf(bank, credit), repo)
        assertEquals(credit.id, m.account?.id)
        assertEquals(AccountLinkConfidence.CONFIRMED, m.level)
    }

    @Test
    fun madaLastFourLinksToBankAccountNotCredit() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT)
        val credit = account(2, AccountType.CREDIT_CARD)
        val repo = repoWith(Triple(bank, AccountIdentifierType.DEBIT_CARD_LAST4, "8219"))
        val m = AccountMatcher.match(tx("8219", FinancialTreatment.EXPENSE), listOf(bank, credit), repo)
        assertEquals(bank.id, m.account?.id)
    }

    @Test
    fun senderAloneWithMultipleAccountsRequiresReview() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT)
        val card = account(2, AccountType.CREDIT_CARD)
        val repo = repoWith(
            Triple(bank, AccountIdentifierType.SENDER_ALIAS, "bank"),
            Triple(card, AccountIdentifierType.SENDER_ALIAS, "bank"),
        )
        val m = AccountMatcher.match(tx(null, FinancialTreatment.EXPENSE), listOf(bank, card), repo)
        assertNull(m.account)
        assertEquals(AccountLinkConfidence.UNMATCHED, m.level)
    }

    @Test
    fun firstDatabaseRowIsNeverTieBreaker() = runBlocking {
        val bank1 = account(1, AccountType.BANK_ACCOUNT)
        val bank2 = account(2, AccountType.BANK_ACCOUNT)
        val repo = repoWith(
            Triple(bank1, AccountIdentifierType.ACCOUNT_LAST4, "7271"),
            Triple(bank2, AccountIdentifierType.ACCOUNT_LAST4, "7271"),
        )
        val m = AccountMatcher.match(tx("7271", FinancialTreatment.EXPENSE), listOf(bank1, bank2), repo)
        assertNull("must require review when typed identifier exists on multiple accounts", m.account)
        assertTrue(m.needsReview)
    }

    @Test
    fun institutionNameAloneNeverSelectsAccount() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT).copy(institutionName = "Bank")
        val repo = repoWith(Triple(bank, AccountIdentifierType.ACCOUNT_LAST4, "9999"))
        val m = AccountMatcher.match(
            tx(null, FinancialTreatment.EXPENSE).copy(
                originalSender = "unknown-sender",
                merchantOrBeneficiary = "purchase at Bank store",
            ),
            listOf(bank),
            repo,
        )
        assertNull(m.account)
        assertEquals("missing_account_identifier", m.diagnosticCode)
    }

    @Test
    fun withoutTypedSenderAliasSenderDoesNotMatch() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT)
        val accountDao = FakeAccountDao(
            listOf(
                FinancialAccountEntity(
                    id = bank.id,
                    displayName = bank.displayName,
                    institutionName = bank.institutionName,
                    accountType = bank.accountType,
                    accountNature = bank.accountNature,
                    currency = bank.currency,
                    openingBalance = bank.openingBalance,
                    openingBalanceDate = bank.openingBalanceDate,
                    includeInNetWorth = bank.includeInNetWorth,
                    includeInLiquidity = bank.includeInLiquidity,
                    isOwnedByUser = true,
                    systemAccountKey = null,
                    isActive = true,
                    notes = null,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            ),
        )
        val repo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
        val m = AccountMatcher.match(
            tx(null).copy(originalSender = "alrajhi"),
            listOf(bank),
            repo,
        )
        assertNull("sender must not match without typed SENDER_ALIAS", m.account)
    }
}
