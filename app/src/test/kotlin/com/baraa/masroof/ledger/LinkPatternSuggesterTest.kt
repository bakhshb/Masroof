package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierEntity
import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.AccountLinkRuleDao
import com.baraa.masroof.data.db.AccountLinkRuleEntity
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import com.baraa.masroof.transaction.FinancialTreatment
import com.baraa.masroof.transaction.TransactionStatus
import com.baraa.masroof.transaction.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LinkPatternSuggesterTest {
    private class FakeRuleDao : AccountLinkRuleDao {
        val rules = mutableListOf<AccountLinkRuleEntity>()
        var idSeq = 0L
        override fun observeAll() = MutableStateFlow(rules.toList())
        override suspend fun bySignature(signature: String) = rules.firstOrNull { it.signature == signature }
        override suspend fun insert(rule: AccountLinkRuleEntity): Long {
            idSeq += 1
            rules += rule.copy(id = idSeq)
            return idSeq
        }
        override suspend fun update(rule: AccountLinkRuleEntity): Int {
            val i = rules.indexOfFirst { it.id == rule.id }
            if (i >= 0) {
                rules[i] = rule
                return 1
            }
            return 0
        }
        override suspend fun delete(rule: AccountLinkRuleEntity): Int {
            val i = rules.indexOfFirst { it.id == rule.id }
            if (i >= 0) {
                rules.removeAt(i)
                return 1
            }
            return 0
        }
    }

    private fun accountEntity(
        id: Long,
        name: String = "A$id",
        type: AccountType = AccountType.BANK_ACCOUNT,
        institution: String? = "Bank",
    ) = FinancialAccountEntity(
        id = id,
        displayName = name,
        institutionName = institution,
        accountType = type,
        accountNature = AccountNature.defaultNatureFor(type),
        currency = Currency.SAR,
        openingBalance = BigDecimal.ZERO,
        openingBalanceDate = 0L,
        includeInNetWorth = true,
        includeInLiquidity = true,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun account(
        id: Long,
        name: String = "A$id",
        type: AccountType = AccountType.BANK_ACCOUNT,
        institution: String? = "Bank",
    ) = FinancialAccount(
        id, name, institution, type, AccountNature.defaultNatureFor(type), Currency.SAR,
        BigDecimal.ZERO, 1, true, true, true, null, true, null,
    )

    private fun tx(
        last4: String? = "1234",
        type: TransactionType = TransactionType.PURCHASE,
        treatment: FinancialTreatment = FinancialTreatment.PENDING_REVIEW,
        sender: String? = "AlRajhi",
    ) = TransactionEntity(
        id = 1,
        uniqueFingerprint = "u1",
        smsTimestamp = 0,
        originalSender = sender,
        transactionType = type,
        amount = BigDecimal("50"),
        currency = Currency.SAR,
        merchantOrBeneficiary = "Store",
        accountOrCardLastFourDigits = last4,
        transactionDate = LocalDate.now(),
        transactionTime = null,
        status = TransactionStatus.COMPLETED,
        confidence = 80,
        parsingNotes = emptyList(),
        dateSource = com.baraa.masroof.data.db.DateSource.FROM_BODY,
        createdAt = 0,
        updatedAt = 0,
        financialTreatment = treatment,
        postingStatus = TransactionPostingStatus.NEEDS_REVIEW,
    )

    @Test
    fun suggestsExpenseFromPurchasePatternAndLastFour() = runBlocking {
        val idDao = FakeIdentifierDao()
        idDao.insert(
            AccountIdentifierEntity(
                accountId = 10,
                identifierType = AccountIdentifierType.DEBIT_CARD_LAST4,
                displayLabel = "مدى",
                normalizedValue = "1234",
                isActive = true,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        val accountDao = FakeAccountDao(listOf(accountEntity(10), accountEntity(20, name = "Other")))
        val rules = AccountLinkRuleRepository(FakeRuleDao())
        val suggester = LinkPatternSuggester(
            AccountIdentifierRepository(idDao, accountDao),
            rules,
        )
        val accounts = listOf(account(10), account(20, name = "Other"))
        val suggestion = suggester.suggest(tx(), accounts)
        assertNotNull(suggestion)
        assertEquals(FinancialTreatment.EXPENSE, suggestion!!.treatment)
        assertEquals(10L, suggestion.sourceAccountId)
        assertNull(suggestion.destinationAccountId)
        assertTrue(suggestion.reasonAr.isNotBlank())
    }

    @Test
    fun learnsLastFourRuleAndReapplies() = runBlocking {
        val ruleDao = FakeRuleDao()
        val rules = AccountLinkRuleRepository(ruleDao)
        val suggester = LinkPatternSuggester(
            AccountIdentifierRepository(FakeIdentifierDao(), FakeAccountDao()),
            rules,
        )
        val accounts = listOf(account(5, name = "حساب"))
        val confirmed = tx(last4 = "5678", type = TransactionType.PURCHASE)
            .copy(financialTreatment = FinancialTreatment.EXPENSE)
        rules.remember(confirmed, accounts.single(), AccountLinkRuleRepository.DIRECTION_SOURCE)
        assertTrue(AccountLinkRuleRepository.signature(confirmed).contains("5678"))

        val again = tx(last4 = "5678", type = TransactionType.PURCHASE, treatment = FinancialTreatment.EXPENSE)
        assertEquals(5L, rules.find(again, accounts, AccountLinkRuleRepository.DIRECTION_SOURCE)?.id)

        val suggestion = suggester.suggest(
            again.copy(financialTreatment = FinancialTreatment.PENDING_REVIEW),
            accounts,
        )
        assertNotNull(suggestion)
        assertEquals(5L, suggestion!!.sourceAccountId)
        assertEquals(FinancialTreatment.EXPENSE, suggestion.treatment)
    }

    @Test
    fun ambiguousMultiAccountWithoutLastFourReturnsNull() = runBlocking {
        val suggester = LinkPatternSuggester(
            AccountIdentifierRepository(FakeIdentifierDao(), FakeAccountDao()),
            AccountLinkRuleRepository(FakeRuleDao()),
        )
        val accounts = listOf(
            account(1, name = "A", institution = "X"),
            account(2, name = "B", institution = "Y"),
        )
        assertNull(
            suggester.suggest(tx(last4 = null, sender = "UnknownSenderXYZ"), accounts),
        )
    }

    @Test
    fun signatureNeverContainsAmountOrBody() {
        val t = tx().copy(amount = BigDecimal("999999"), merchantOrBeneficiary = "secret body text")
        val sig = AccountLinkRuleRepository.signature(t)
        assertFalse(sig.contains("999999"))
        assertFalse(sig.contains("secret"))
    }
}
