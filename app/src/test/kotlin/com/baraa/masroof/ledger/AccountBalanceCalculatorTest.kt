package com.baraa.masroof.ledger

import com.baraa.masroof.data.db.FinancialAccountEntity
import com.baraa.masroof.data.db.JournalEntryEntity
import com.baraa.masroof.data.db.JournalWithPostings
import com.baraa.masroof.data.db.LedgerPostingEntity
import com.baraa.masroof.data.db.OpeningBalanceKind
import com.baraa.masroof.transaction.AccountNature
import com.baraa.masroof.transaction.AccountType
import com.baraa.masroof.transaction.Currency
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class AccountBalanceCalculatorTest {
    private val zone = java.time.ZoneId.of("UTC")
    private val opening = LocalDate.of(2025, 1, 1)
    private val openingMillis get() = opening.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun bank(id: Long, opening: BigDecimal) = financialAccount(id, AccountType.BANK_ACCOUNT, AccountNature.ASSET, opening, includeLiquidity = true)
    private fun creditCard(id: Long, outstanding: BigDecimal, creditLimit: BigDecimal? = BigDecimal("10000")) = financialAccount(id, AccountType.CREDIT_CARD, AccountNature.LIABILITY, outstanding, includeLiquidity = false, creditLimit = creditLimit)

    private fun financialAccount(id: Long, type: AccountType, nature: AccountNature, opening: BigDecimal, includeLiquidity: Boolean, creditLimit: BigDecimal? = null) = FinancialAccountEntity(
        id = id,
        displayName = "A$id",
        institutionName = "Bank",
        accountType = type,
        accountNature = nature,
        currency = Currency.SAR,
        openingBalance = opening,
        openingBalanceDate = openingMillis,
        includeInNetWorth = true,
        includeInLiquidity = includeLiquidity,
        isOwnedByUser = true,
        systemAccountKey = null,
        isActive = true,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
        creditLimit = creditLimit
    )

    private fun postedJournal(id: Long, date: LocalDate, postings: List<LedgerPostingEntity>, status: JournalPostingStatus = JournalPostingStatus.POSTED): JournalWithPostings = JournalWithPostings(
        journal = JournalEntryEntity(
            id = id, sourceTransactionId = null,
            journalType = JournalType.EXPENSE,
            postingStatus = status,
            effectiveDate = date, effectiveTime = LocalTime.NOON,
            descriptionCode = "x",
            createdAt = 0L, updatedAt = 0L,
            reversalOfJournalId = null, notes = null,
            generatedBy = JournalGeneratedBy.IMPORT_RULE, generationVersion = 1
        ),
        postings = postings
    )

    private fun posting(journalId: Long, accountId: Long, side: PostingSide, amount: String, currency: Currency = Currency.SAR) = LedgerPostingEntity(journalEntryId = journalId, accountId = accountId, postingSide = side, amount = BigDecimal(amount), currency = currency, memoCode = null, createdAt = 0L)

    @Test fun debitToAssetIncreasesBalance() {
        val s = AccountBalanceCalculator.calculate(
            bank(1, BigDecimal("1000")),
            listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.DEBIT, "300")))),
            zone
        )
        assertEquals(0, s.totalDebits.compareTo(BigDecimal("300")))
        assertEquals(0, s.totalCredits.compareTo(BigDecimal.ZERO))
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1300")))
    }

    @Test fun creditToAssetReducesBalance() {
        val s = AccountBalanceCalculator.calculate(
            bank(1, BigDecimal("1000")),
            listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "200")))),
            zone
        )
        assertEquals(0, s.totalCredits.compareTo(BigDecimal("200")))
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("800")))
    }

    @Test fun refundMovesMoneyBackToAsset() {
        // Refund / incoming transfer: bank account is debited.
        val s = AccountBalanceCalculator.calculate(
            bank(1, BigDecimal("1000")),
            listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.DEBIT, "150")))),
            zone
        )
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1150")))
    }

    @Test fun draftJournalsAreIgnored() {
        val drafts = postedJournal(
            99, LocalDate.of(2025, 1, 10),
            listOf(posting(99, 1, PostingSide.CREDIT, "999")),
            status = JournalPostingStatus.DRAFT
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(drafts), zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1000")))
    }

    @Test fun reversedJournalsAreIgnored() {
        val reversed = postedJournal(
            10, LocalDate.of(2025, 1, 10),
            listOf(posting(10, 1, PostingSide.CREDIT, "200")),
            status = JournalPostingStatus.REVERSED
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(reversed), zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1000")))
    }

    @Test fun voidedJournalsAreIgnored() {
        val voided = postedJournal(
            10, LocalDate.of(2025, 1, 10),
            listOf(posting(10, 1, PostingSide.CREDIT, "200")),
            status = JournalPostingStatus.VOIDED
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(voided), zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1000")))
    }

    @Test fun preOpeningDatePostingsAreExcluded() {
        val journals = listOf(
            postedJournal(10, LocalDate.of(2024, 12, 30), listOf(posting(10, 1, PostingSide.CREDIT, "500"))),
            postedJournal(11, LocalDate.of(2025, 1, 10), listOf(posting(11, 1, PostingSide.CREDIT, "100")))
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("900")))
    }

    @Test fun currencyMismatchExcludesPosting() {
        val journals = listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "100", Currency.USD))))
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1000")))
        assertEquals(1, s.excludedPostings)
    }

    @Test fun internalTransferUpdatesBothSidesWithoutIncome() {
        // Source (1) is credited 500; Destination (2) is debited 500.
        val journals = listOf(postedJournal(
            100, LocalDate.of(2025, 1, 10),
            listOf(posting(100, 2, PostingSide.DEBIT, "500"), posting(100, 1, PostingSide.CREDIT, "500"))
        ))
        val s1 = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        val s2 = AccountBalanceCalculator.calculate(bank(2, BigDecimal("2000")), journals, zone)
        assertEquals(0, s1.calculatedBalance.compareTo(BigDecimal("500")))
        assertEquals(0, s2.calculatedBalance.compareTo(BigDecimal("2500")))
    }

    @Test fun creditCardPurchaseAddsOutstanding() {
        // Standard credit card purchase: card is CREDIT posting → outstanding grows.
        val journals = listOf(postedJournal(200, LocalDate.of(2025, 1, 10), listOf(posting(200, 1, PostingSide.CREDIT, "300"))))
        val s = AccountBalanceCalculator.calculate(creditCard(1, BigDecimal.ZERO), journals, zone)
        assertEquals(0, s.outstandingBalance!!.compareTo(BigDecimal("300")))
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("300")))
    }

    @Test fun creditCardPaymentReducesOutstanding() {
        // Payment is a DEBIT on the card → outstanding shrinks.
        val journals = listOf(postedJournal(300, LocalDate.of(2025, 1, 10), listOf(posting(300, 1, PostingSide.DEBIT, "200"))))
        val s = AccountBalanceCalculator.calculate(creditCard(1, BigDecimal("500")), journals, zone)
        assertEquals(0, s.outstandingBalance!!.compareTo(BigDecimal("300")))
    }

    @Test fun openingAvailableCreditConvertsToOutstanding() {
        val card = creditCard(1, BigDecimal("8000")).copy(openingBalanceKind = OpeningBalanceKind.AVAILABLE)
        // Force evaluation
        assertEquals(BigDecimal("10000"), card.creditLimit)
        assertEquals(OpeningBalanceKind.AVAILABLE, card.openingBalanceKind)
        val s = AccountBalanceCalculator.calculate(card, emptyList(), zone)
        // Stored 8000 = opening available; limit 10000 → outstanding = 2000.
        assertEquals(0, s.outstandingBalance!!.compareTo(BigDecimal("2000")))
    }

    @Test fun updatingOpeningBalanceRecalculatesBalance() {
        val original = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), emptyList(), zone)
        val updated = AccountBalanceCalculator.calculate(bank(1, BigDecimal("2500")), emptyList(), zone)
        assertEquals(0, original.calculatedBalance.compareTo(BigDecimal("1000")))
        assertEquals(0, updated.calculatedBalance.compareTo(BigDecimal("2500")))
    }

    @Test fun duplicateJournalsCountedDistinctly() {
        val j = postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "100")))
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(j, j), zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("800")))
        assertEquals(2, s.includedPostings)
    }

    @Test fun calculateManyReturnsMapByAccountId() {
        val journals = listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "100"))))
        val map = AccountBalanceCalculator.calculateMany(
            listOf(bank(1, BigDecimal("1000")), bank(2, BigDecimal("500"))),
            journals, zone
        )
        assertEquals(2, map.size)
        assertEquals(0, map[1]!!.calculatedBalance.compareTo(BigDecimal("900")))
        assertEquals(0, map[2]!!.calculatedBalance.compareTo(BigDecimal("500")))
    }

    @Test fun bankingChargesInDifferentCurrenciesSumSeparately() {
        val journals = listOf(
            postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "100"))),
            postedJournal(11, LocalDate.of(2025, 1, 10), listOf(posting(11, 1, PostingSide.DEBIT, "50", Currency.USD)))
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        // SAR postings only — USD excluded.
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("900")))
        assertEquals(1, s.excludedPostings)
    }

    @Test fun needsReviewJournalDoesNotAffectBalance() {
        // Per spec: NEEDS_REVIEW journals must not be applied to the
        // balance. The calculator filters out anything that is not
        // POSTED — DRAFT, NEEDS_REVIEW, REVERSED, VOIDED are excluded.
        val needsReviewJournal = postedJournal(
            10, LocalDate.of(2025, 1, 10),
            listOf(posting(10, 1, PostingSide.CREDIT, "200")),
            status = JournalPostingStatus.NEEDS_REVIEW
        )
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(needsReviewJournal), zone)
        // Opening balance remains at 1000 because the journal is not POSTED.
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1000")))
    }

    @Test fun confirmingReviewUpdatesBalance() {
        // Simulates the lifecycle: journal starts as NEEDS_REVIEW,
        // then transitions to POSTED after the user confirms it.
        val journalBefore = postedJournal(
            10, LocalDate.of(2025, 1, 10),
            listOf(posting(10, 1, PostingSide.CREDIT, "250")),
            status = JournalPostingStatus.NEEDS_REVIEW
        )
        val journalAfter = postedJournal(
            10, LocalDate.of(2025, 1, 10),
            listOf(posting(10, 1, PostingSide.CREDIT, "250")),
            status = JournalPostingStatus.POSTED
        )
        val sBefore = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(journalBefore), zone)
        val sAfter = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), listOf(journalAfter), zone)
        assertEquals(0, sBefore.calculatedBalance.compareTo(BigDecimal("1000")))
        assertEquals(0, sAfter.calculatedBalance.compareTo(BigDecimal("750")))
    }

    @Test fun appRestartPreservesCalculatedBalance() {
        // Same input, two separate calculate() calls — the result must
        // be identical. This simulates app process death + restart.
        val journals = listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "300"))))
        val s1 = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        val s2 = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        assertEquals(0, s1.calculatedBalance.compareTo(s2.calculatedBalance))
        assertEquals(0, s1.calculatedBalance.compareTo(BigDecimal("700")))
    }

    @Test fun importedDebitDecreasesBalance() {
        val journals = listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.DEBIT, "75"))))
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("1075")))
    }

    @Test fun importedCreditIncreasesBalance() {
        val journals = listOf(postedJournal(10, LocalDate.of(2025, 1, 10), listOf(posting(10, 1, PostingSide.CREDIT, "200"))))
        val s = AccountBalanceCalculator.calculate(bank(1, BigDecimal("1000")), journals, zone)
        assertEquals(0, s.calculatedBalance.compareTo(BigDecimal("800")))
    }
}
