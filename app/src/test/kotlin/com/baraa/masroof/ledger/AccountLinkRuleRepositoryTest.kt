package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountLinkRuleDao
import com.baraa.masroof.data.db.AccountLinkRuleEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AccountLinkRuleRepositoryTest {
    private class FakeDao : AccountLinkRuleDao {
        val rules = mutableListOf<AccountLinkRuleEntity>()
        var idSeq = 0L
        override fun observeAll() = kotlinx.coroutines.flow.MutableStateFlow(rules.toList())
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

    private fun account(id: Long, type: AccountType = AccountType.BANK_ACCOUNT) = FinancialAccount(
        id, "A$id", "Bank", type, AccountNature.defaultNatureFor(type), Currency.SAR,
        BigDecimal.ZERO, 1, true, true, true, null, true, null,
    )

    private fun tx(
        type: TransactionType = TransactionType.PURCHASE,
        treatment: FinancialTreatment = FinancialTreatment.EXPENSE,
    ) = TransactionEntity(
        id = 1,
        uniqueFingerprint = "u1",
        smsTimestamp = 0,
        originalSender = "bank",
        transactionType = type,
        amount = BigDecimal("100"),
        currency = Currency.SAR,
        merchantOrBeneficiary = null,
        accountOrCardLastFourDigits = null,
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
    fun rememberIsNoopWhenUncheckedByDefault() = runBlocking {
        val dao = FakeDao()
        AccountLinkRuleRepository(dao)
        assertEquals(0, dao.rules.size)
    }

    @Test
    fun checkedConfirmationCreatesRule() = runBlocking {
        val dao = FakeDao()
        val repo = AccountLinkRuleRepository(dao)
        repo.remember(tx(), account(1), "source")
        assertEquals(1, dao.rules.size)
        assertTrue(dao.rules.single().active)
    }

    @Test
    fun existingRuleIsUpdatedInsteadOfDuplicated() = runBlocking {
        val dao = FakeDao()
        val repo = AccountLinkRuleRepository(dao)
        val a = tx()
        repo.remember(a, account(1), "source")
        val before = dao.rules.single().confirmationCount
        repo.remember(a, account(1), "source")
        assertEquals(1, dao.rules.size)
        assertEquals(before + 1, dao.rules.single().confirmationCount)
    }

    @Test
    fun findRespectsActiveFlag() = runBlocking {
        val dao = FakeDao()
        val repo = AccountLinkRuleRepository(dao)
        repo.remember(tx(), account(1), "source")
        val rule = dao.rules.single()
        dao.update(rule.copy(active = false))
        assertNull(repo.find(tx(), listOf(account(1))))
    }

    @Test
    fun signatureExcludesBodyAndAmount() {
        val tx = tx().copy(amount = BigDecimal("999999"), merchantOrBeneficiary = "secret message body")
        val signature = AccountLinkRuleRepository.signature(tx)
        assertFalse(signature.contains("999999"))
        assertFalse(signature.contains("secret"))
        assertTrue(signature.contains("|"))
    }

    @Test
    fun findAppliesEvenWhenLastFourPresent() = runBlocking {
        val dao = FakeDao()
        val repo = AccountLinkRuleRepository(dao)
        val withLast4 = tx().copy(
            accountOrCardLastFourDigits = "4242",
            financialTreatment = FinancialTreatment.EXPENSE,
        )
        repo.remember(withLast4, account(1), AccountLinkRuleRepository.DIRECTION_SOURCE)
        val found = repo.find(withLast4, listOf(account(1)), AccountLinkRuleRepository.DIRECTION_SOURCE)
        assertEquals(1L, found?.id)
    }
}
