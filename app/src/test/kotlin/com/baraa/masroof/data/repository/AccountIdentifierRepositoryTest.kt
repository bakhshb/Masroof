package com.baraa.masroof.data.repository

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.ledger.FakeAccountDao
import com.baraa.masroof.ledger.FakeIdentifierDao
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class AccountIdentifierRepositoryTest {
    private fun bank(id: Long, type: AccountType = AccountType.BANK_ACCOUNT, institution: String? = "Bank") = FinancialAccountEntity(
        id = id, displayName = "A$id", institutionName = institution, accountType = type,
        accountNature = AccountNature.ASSET,
        currency = Currency.SAR, openingBalance = BigDecimal.ZERO, openingBalanceDate = 0L,
        includeInNetWorth = true, includeInLiquidity = type == AccountType.BANK_ACCOUNT,
        isOwnedByUser = true, systemAccountKey = null, isActive = true, notes = null,
        createdAt = 0L, updatedAt = 0L
    )

    private fun newRepo(vararg entities: FinancialAccountEntity): AccountIdentifierRepository {
        val accountDao = FakeAccountDao(entities.toList())
        return AccountIdentifierRepository(FakeIdentifierDao(), accountDao)
    }

    @Test fun addAccountLastFourStoresNormalizedArabicDigits() = runBlocking {
        val repo = newRepo(bank(1))
        val outcome = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "حساب", "١٢٣٤"))
        assertEquals(IdentifierAddResult.Added, outcome.result)
        val stored = repo.getForAccount(1).single()
        assertEquals("1234", stored.normalizedValue)
    }

    @Test fun duplicateOnSameAccountUpdatesLabel() = runBlocking {
        val repo = newRepo(bank(1))
        repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "Old", "1234"))
        val outcome = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "New", "1234"))
        assertEquals(IdentifierAddResult.Updated, outcome.result)
        val stored = repo.getForAccount(1).single()
        assertEquals("New", stored.displayLabel)
        assertEquals(1, repo.getForAccount(1).size)
    }

    @Test fun duplicateAcrossAccountsFlagsConflict() = runBlocking {
        val repo = newRepo(bank(1, type = AccountType.CREDIT_CARD), bank(2, type = AccountType.CREDIT_CARD))
        repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.CREDIT_CARD_LAST4, "A1", "7271"))
        val outcome = repo.addOrUpdate(2, IdentifierForm(AccountIdentifierType.CREDIT_CARD_LAST4, "A2", "7271"))
        assertEquals(IdentifierAddResult.AddedWithConflict, outcome.result)
        assertNotNull(outcome.message)
        assertEquals(1, outcome.conflictingAccounts.size)
    }

    @Test fun disabledIdentifierNotUsedForLookup() = runBlocking {
        val repo = newRepo(bank(1))
        repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.DEBIT_CARD_LAST4, "Active", "8219"))
        val ids = repo.getForAccount(1)
        repo.setActive(ids.single().id, false)
        val found = repo.findAccountsByIdentifier(AccountIdentifierType.DEBIT_CARD_LAST4, "8219")
        assertTrue(found.isEmpty())
    }

    @Test fun senderAliasMatchingIsCaseInsensitive() = runBlocking {
        val repo = newRepo(bank(1))
        repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, "AlRajhi", "alrajhi"))
        val matches = repo.accountsForSender("ALRAJHI")
        assertEquals(1, matches.size)
        assertEquals(1L, matches.single().id)
    }

    @Test fun incompatibleIdentifierTypeIsRejected() = runBlocking {
        val repo = newRepo(bank(1, type = AccountType.BANK_ACCOUNT))
        val outcome = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.CREDIT_CARD_LAST4, "x", "1234"))
        assertEquals(IdentifierAddResult.Rejected, outcome.result)
    }

    @Test fun legacyColumnBackfillIsNoOpAfterSchemaRetirement() = runBlocking {
        val repo = newRepo(bank(1), bank(2, type = AccountType.CREDIT_CARD))
        assertEquals(0, repo.backfillFromLegacyLastFour())
        assertEquals(0, repo.backfillFromLegacySenderAliases())
        assertTrue(repo.getForAccount(1).isEmpty())
        assertTrue(repo.getForAccount(2).isEmpty())
    }

    @Test fun ensureLegacyIdentifierBackfillIsIdempotentNoOp() = runBlocking {
        val repo = newRepo(bank(1, type = AccountType.CREDIT_CARD))
        repo.ensureLegacyIdentifierBackfill()
        repo.ensureLegacyIdentifierBackfill()
        assertTrue(repo.getForAccount(1).isEmpty())
    }

    @Test fun updateValueChangesNormalizedDigits() = runBlocking {
        val repo = newRepo(bank(1))
        val added = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "Old", "1234"))
        val outcome = repo.updateValue(added.identifier!!.id, "5678", "New")
        assertEquals(IdentifierAddResult.Added, outcome.result)
        assertEquals("5678", outcome.identifier?.normalizedValue)
        assertEquals(1, repo.getForAccount(1).size)
    }

    @Test fun senderAliasRejectsBlank() = runBlocking {
        val repo = newRepo(bank(1))
        val outcome = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.SENDER_ALIAS, "x", "   "))
        assertEquals(IdentifierAddResult.Rejected, outcome.result)
    }

    @Test fun ibanLastFourStoredAsFourDigits() = runBlocking {
        val repo = newRepo(bank(1))
        val outcome = repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.IBAN_LAST4, "IBAN", "SA0380000000608010167519"))
        assertEquals(IdentifierAddResult.Added, outcome.result)
        assertEquals("7519", outcome.identifier?.normalizedValue)
    }

    @Test fun sameBankAccountAcceptsMultipleDistinctLastFours() = runBlocking {
        val repo = newRepo(bank(1))
        assertEquals(
            IdentifierAddResult.Added,
            repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "حساب", "1111")).result,
        )
        assertEquals(
            IdentifierAddResult.Added,
            repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.DEBIT_CARD_LAST4, "مدى", "2222")).result,
        )
        assertEquals(
            IdentifierAddResult.Added,
            repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.IBAN_LAST4, "آيبان", "3333")).result,
        )
        assertEquals(
            IdentifierAddResult.Added,
            repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.ACCOUNT_LAST4, "حساب2", "4444")).result,
        )
        val values = repo.getForAccount(1).map { it.normalizedValue }.toSet()
        assertEquals(setOf("1111", "2222", "3333", "4444"), values)
    }

    @Test fun creditCardAcceptsThreeDistinctLastFours() = runBlocking {
        val card = bank(1, type = AccountType.CREDIT_CARD).copy(accountNature = AccountNature.LIABILITY)
        val repo = newRepo(card)
        for (last in listOf("1111", "2222", "3333")) {
            assertEquals(
                IdentifierAddResult.Added,
                repo.addOrUpdate(1, IdentifierForm(AccountIdentifierType.CREDIT_CARD_LAST4, "فيزا", last)).result,
            )
        }
        assertEquals(3, repo.getForAccount(1).size)
        assertEquals(
            setOf("1111", "2222", "3333"),
            repo.findAccountsByLastFourAnyType("2222").map { it.id }.toSet().let {
                // ensure lookup finds the card
                assertTrue(it.contains(1L))
                repo.getForAccount(1).map { row -> row.normalizedValue }.toSet()
            },
        )
    }
}
