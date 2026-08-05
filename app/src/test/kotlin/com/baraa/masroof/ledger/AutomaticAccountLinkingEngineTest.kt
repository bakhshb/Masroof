package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.transaction.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class AutomaticAccountLinkingEngineTest {
    private fun account(id: Long, type: AccountType, last: String?, alias: String = "bank") = FinancialAccount(id, "A$id", "Bank", type, AccountNature.defaultNatureFor(type), last, listOf(alias), Currency.SAR, BigDecimal.ZERO, 1, true, true, true, null, true, null)
    private fun tx(last: String?, treatment: FinancialTreatment = FinancialTreatment.EXPENSE) = TransactionEntity(1,"x",1,"bank",TransactionType.PURCHASE,BigDecimal.ONE,Currency.SAR,null,last,null,null,TransactionStatus.COMPLETED,100, emptyList(),com.baraa.masroof.data.db.DateSource.FROM_BODY,1,1, financialTreatment=treatment)

    private fun repoWith(vararg triples: Triple<FinancialAccount, AccountIdentifierType, String>): AccountIdentifierRepository {
        val repo = AccountIdentifierRepository(FakeIdentifierDao(), FakeAccountDao())
        for ((account, type, value) in triples) {
            val entity = FinancialAccountEntity(
                id = account.id,
                displayName = account.displayName,
                institutionName = account.institutionName,
                accountType = account.accountType,
                accountNature = account.accountNature,
                lastFourDigits = null,
                senderAliases = account.senderAliases.joinToString(","),
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
            val mapField = FakeAccountDao::class.java.getDeclaredField("accounts")
            mapField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (mapField.get(repo.javaClass.getDeclaredField("accountDao").apply { isAccessible = true }.get(repo)) as MutableMap<Long, FinancialAccountEntity>)[account.id] = entity
            runBlocking {
                repo.addOrUpdate(account.id, IdentifierForm(type, account.displayName, value))
            }
        }
        return repo
    }

    @Test fun creditCardLastFourLinksToCreditCardNotBank() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT, null)
        val credit = account(2, AccountType.CREDIT_CARD, null)
        val repo = repoWith(Triple(bank, AccountIdentifierType.SENDER_ALIAS, "bank"), Triple(credit, AccountIdentifierType.CREDIT_CARD_LAST4, "7271"))
        val m = AccountMatcher.match(tx("7271", FinancialTreatment.EXPENSE), listOf(bank, credit), repo)
        assertEquals(credit.id, m.account?.id)
        assertEquals(AccountLinkConfidence.CONFIRMED, m.level)
    }

    @Test fun madaLastFourLinksToBankAccountNotCredit() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT, null)
        val credit = account(2, AccountType.CREDIT_CARD, null)
        val repo = repoWith(Triple(bank, AccountIdentifierType.DEBIT_CARD_LAST4, "8219"))
        val m = AccountMatcher.match(tx("8219", FinancialTreatment.EXPENSE), listOf(bank, credit), repo)
        assertEquals(bank.id, m.account?.id)
    }

    @Test fun senderAloneWithMultipleAccountsRequiresReview() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT, null)
        val card = account(2, AccountType.CREDIT_CARD, null)
        val repo = repoWith(Triple(bank, AccountIdentifierType.SENDER_ALIAS, "bank"), Triple(card, AccountIdentifierType.SENDER_ALIAS, "bank"))
        val m = AccountMatcher.match(tx(null, FinancialTreatment.EXPENSE), listOf(bank, card), repo)
        assertNull(m.account)
        assertEquals(AccountLinkConfidence.UNMATCHED, m.level)
    }

    @Test fun firstDatabaseRowIsNeverTieBreaker() = runBlocking {
        val bank1 = account(1, AccountType.BANK_ACCOUNT, null)
        val bank2 = account(2, AccountType.BANK_ACCOUNT, null)
        val repo = repoWith(Triple(bank1, AccountIdentifierType.ACCOUNT_LAST4, "7271"), Triple(bank2, AccountIdentifierType.ACCOUNT_LAST4, "7271"))
        val m = AccountMatcher.match(tx("7271", FinancialTreatment.EXPENSE), listOf(bank1, bank2), repo)
        assertNull("must require review when typed identifier exists on multiple accounts", m.account)
        assertTrue(m.needsReview)
    }
}
