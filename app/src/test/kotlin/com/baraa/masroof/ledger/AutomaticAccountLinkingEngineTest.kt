package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.AccountIdentifierType
import com.baraa.masroof.data.db.AccountSenderProfileCrossRef
import com.baraa.masroof.data.db.FinancialAccount
import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.SenderProfileEntity
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.AccountIdentifierRepository
import com.baraa.masroof.data.repository.IdentifierForm
import com.baraa.masroof.sms.SenderNormalizer
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

    private fun repoWith(
        vararg triples: Triple<FinancialAccount, AccountIdentifierType, String>,
        senderLinks: List<Pair<Long, String>> = emptyList(),
    ): AccountIdentifierRepository {
        val accountDao = FakeAccountDao()
        val profileDao = FakeSenderProfileDao()
        val linkDao = FakeAccountSenderProfileDao(profileDao) {
            runBlocking {
                accountDao.getActive()
                    .filter { it.isOwnedByUser && it.systemAccountKey == null }
                    .map { it.id }
                    .toSet()
            }
        }
        val repo = AccountIdentifierRepository(FakeIdentifierDao(), accountDao, profileDao, linkDao)
        val inserted = mutableSetOf<Long>()
        for ((account, type, value) in triples) {
            if (account.id !in inserted) {
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
                inserted += account.id
            }
            runBlocking {
                repo.addOrUpdate(account.id, IdentifierForm(type, account.displayName, value))
            }
        }
        runBlocking {
            for ((accountId, rawSender) in senderLinks) {
                if (accountId !in inserted) {
                    val acct = triples.first { it.first.id == accountId }.first
                    accountDao.insert(
                        FinancialAccountEntity(
                            id = acct.id,
                            displayName = acct.displayName,
                            institutionName = acct.institutionName,
                            accountType = acct.accountType,
                            accountNature = acct.accountNature,
                            currency = acct.currency,
                            openingBalance = acct.openingBalance,
                            openingBalanceDate = acct.openingBalanceDate,
                            includeInNetWorth = acct.includeInNetWorth,
                            includeInLiquidity = acct.includeInLiquidity,
                            isOwnedByUser = acct.isOwnedByUser,
                            systemAccountKey = acct.systemAccountKey,
                            isActive = acct.isActive,
                            notes = acct.notes,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                    )
                    inserted += accountId
                }
                val key = SenderNormalizer.normalize(rawSender) ?: continue
                val existing = profileDao.findByKey(key)
                val profileId = if (existing == null) {
                    profileDao.insert(
                        SenderProfileEntity(
                            displaySender = rawSender,
                            normalizedSenderKey = key,
                            active = true,
                            createdAt = 1L,
                            updatedAt = 1L,
                        ),
                    )
                } else {
                    existing.id
                }
                linkDao.insert(AccountSenderProfileCrossRef(accountId, profileId, 1L))
            }
        }
        return repo
    }

    @Test
    fun creditCardLastFourLinksToCreditCardNotBank() = runBlocking {
        val bank = account(1, AccountType.BANK_ACCOUNT)
        val credit = account(2, AccountType.CREDIT_CARD)
        val repo = repoWith(
            Triple(bank, AccountIdentifierType.ACCOUNT_LAST4, "9999"),
            Triple(credit, AccountIdentifierType.CREDIT_CARD_LAST4, "7271"),
            senderLinks = listOf(1L to "bank", 2L to "bank"),
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
            Triple(bank, AccountIdentifierType.ACCOUNT_LAST4, "1111"),
            Triple(card, AccountIdentifierType.CREDIT_CARD_LAST4, "2222"),
            senderLinks = listOf(1L to "bank", 2L to "bank"),
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
    fun withoutSenderProfileLinkSenderDoesNotMatch() = runBlocking {
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
        assertNull("sender must not match without SenderProfile link", m.account)
    }

    @Test
    fun debitEvidenceMatchesStoredAccountLastFourSameSenderMultiAccount() = runBlocking {
        val a1 = account(1, AccountType.BANK_ACCOUNT)
        val a2 = account(2, AccountType.BANK_ACCOUNT)
        val a3 = account(3, AccountType.BANK_ACCOUNT)
        val card = account(4, AccountType.CREDIT_CARD)
        val accounts = listOf(a1, a2, a3, card)
        val repo = repoWith(
            Triple(a1, AccountIdentifierType.ACCOUNT_LAST4, "1111"),
            Triple(a2, AccountIdentifierType.ACCOUNT_LAST4, "2222"),
            Triple(a3, AccountIdentifierType.ACCOUNT_LAST4, "3333"),
            Triple(card, AccountIdentifierType.CREDIT_CARD_LAST4, "4444"),
            senderLinks = listOf(1L to "bank", 2L to "bank", 3L to "bank", 4L to "bank"),
        )
        val evidence = listOf(
            com.baraa.masroof.transaction.ParsedIdentifierEvidence(
                type = AccountIdentifierType.DEBIT_CARD_LAST4,
                lastFour = "2222",
                role = com.baraa.masroof.transaction.IdentifierRole.SOURCE,
                confidence = 90,
                extractionRule = "label:DEBIT_CARD_LAST4",
            ),
        )
        val m = AccountMatcher.match(
            tx("2222").copy(accountOrCardLastFourDigits = "2222"),
            accounts,
            repo,
            evidence,
        )
        assertEquals(a2.id, m.account?.id)
        assertEquals(false, m.needsReview)
        assertEquals("last_four_cross_type_match", m.diagnosticCode)
        assertEquals(AccountLinkConfidence.CONFIRMED, m.level)
    }

    @Test
    fun creditEvidenceMatchesCreditCardAmongSameSenderAccounts() = runBlocking {
        val a1 = account(1, AccountType.BANK_ACCOUNT)
        val a2 = account(2, AccountType.BANK_ACCOUNT)
        val a3 = account(3, AccountType.BANK_ACCOUNT)
        val card = account(4, AccountType.CREDIT_CARD)
        val accounts = listOf(a1, a2, a3, card)
        val repo = repoWith(
            Triple(a1, AccountIdentifierType.ACCOUNT_LAST4, "1111"),
            Triple(a2, AccountIdentifierType.ACCOUNT_LAST4, "2222"),
            Triple(a3, AccountIdentifierType.ACCOUNT_LAST4, "3333"),
            Triple(card, AccountIdentifierType.CREDIT_CARD_LAST4, "4444"),
            senderLinks = listOf(1L to "bank", 2L to "bank", 3L to "bank", 4L to "bank"),
        )
        val evidence = listOf(
            com.baraa.masroof.transaction.ParsedIdentifierEvidence(
                type = AccountIdentifierType.CREDIT_CARD_LAST4,
                lastFour = "4444",
                role = com.baraa.masroof.transaction.IdentifierRole.UNSPECIFIED,
                confidence = 90,
                extractionRule = "label:CREDIT_CARD_LAST4",
            ),
        )
        val m = AccountMatcher.match(tx("4444"), accounts, repo, evidence)
        assertEquals(card.id, m.account?.id)
        assertEquals(false, m.needsReview)
    }

    @Test
    fun sameSenderWithoutLastFourDoesNotGuessAmongMultipleAccounts() = runBlocking {
        val a1 = account(1, AccountType.BANK_ACCOUNT)
        val a2 = account(2, AccountType.BANK_ACCOUNT)
        val a3 = account(3, AccountType.BANK_ACCOUNT)
        val card = account(4, AccountType.CREDIT_CARD)
        val accounts = listOf(a1, a2, a3, card)
        val repo = repoWith(
            Triple(a1, AccountIdentifierType.ACCOUNT_LAST4, "1111"),
            Triple(a2, AccountIdentifierType.ACCOUNT_LAST4, "2222"),
            Triple(a3, AccountIdentifierType.ACCOUNT_LAST4, "3333"),
            Triple(card, AccountIdentifierType.CREDIT_CARD_LAST4, "4444"),
            senderLinks = listOf(1L to "bank", 2L to "bank", 3L to "bank", 4L to "bank"),
        )
        val m = AccountMatcher.match(tx(null), accounts, repo, emptyList())
        assertNull(m.account)
        assertEquals("ambiguous_sender", m.diagnosticCode)
    }
}
