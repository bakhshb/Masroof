package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.RegistryEntityIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.LoanType
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CommitmentsOverviewBuilderTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val period = FinancialPeriod(
        startDate = LocalDate.parse("2026-07-27"),
        endDateExclusive = LocalDate.parse("2026-08-27"),
    )

    @Test
    fun userCommitmentWithoutRecurrence_countsAsPaidInSourcePeriod() {
        val start = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone)
        val tx = transaction(
            id = "tx-netflix",
            merchant = "Netflix",
            amount = "71",
            at = start.plusSeconds(60),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = tx.id,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(tx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("71.00", Currency.SAR), overview.total)
        assertEquals(Money.of("71.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
    }

    @Test
    fun oneTimeCommitment_outsideSalaryPeriod_isExcluded() {
        val previousPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-06-27"),
            endDateExclusive = LocalDate.parse("2026-07-27"),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = "tx-netflix",
            transactionDate = LocalDate.parse("2026-07-01"),
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)

        val inPreviousPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = previousPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inPreviousPeriod.rows.size)
        assertEquals(CommitmentPaymentStatus.PAID, inPreviousPeriod.rows.single().status)
    }

    @Test
    fun oneTimeCommitment_editedTransactionDate_appearsOnlyInEditedPeriod() {
        val previousPeriod = FinancialPeriod(
            startDate = LocalDate.parse("2026-06-27"),
            endDateExclusive = LocalDate.parse("2026-07-27"),
        )
        val sourceTx = transaction(
            id = "tx-netflix",
            merchant = "Netflix",
            amount = "71",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(previousPeriod.startDate, zone).plusSeconds(60),
        )
        val commitment = commitment(
            name = "Netflix",
            amount = Money.of("71.00", Currency.SAR),
            sourceTransactionId = sourceTx.id,
            transactionDate = LocalDate.parse("2026-08-01"),
        )

        val inSourcePeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = previousPeriod,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertTrue(inSourcePeriod.rows.isEmpty())

        val inEditedPeriod = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(sourceTx),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )
        assertEquals(1, inEditedPeriod.rows.size)
        assertEquals(CommitmentPaymentStatus.PAID, inEditedPeriod.rows.single().status)
    }

    @Test
    fun recurringCommitment_withoutPaymentInPeriod_isUnpaid() {
        val commitment = commitment(
            name = "STC",
            amount = Money.of("173.00", Currency.SAR),
            sourceTransactionId = "tx-old",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(Money.of("173.00", Currency.SAR), overview.total)
        assertEquals(Money.zero(Currency.SAR), overview.paid)
        assertEquals(Money.of("173.00", Currency.SAR), overview.remaining)
        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
    }

    @Test
    fun yearlyCommitment_outsideAnniversaryPeriod_isExcluded() {
        val commitment = commitment(
            name = "Insurance",
            amount = Money.of("1200.00", Currency.SAR),
            sourceTransactionId = "tx-insurance",
            recurrence = CommitmentRecurrence.YEARLY,
            transactionDate = LocalDate.parse("2026-01-15"),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
    }

    @Test
    fun weeklyCommitment_countsEachDueOccurrenceAndPayment() {
        val commitment = commitment(
            name = "Cleaning",
            amount = Money.of("100.00", Currency.SAR),
            sourceTransactionId = "tx-cleaning",
            recurrence = CommitmentRecurrence.WEEKLY,
            transactionDate = LocalDate.parse("2026-07-28"),
        )
        val payment = transaction(
            id = "payment-cleaning",
            merchant = "Cleaning",
            amount = "100",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(5, overview.rows.size)
        assertEquals(Money.of("500.00", Currency.SAR), overview.total)
        assertEquals(Money.of("100.00", Currency.SAR), overview.paid)
        assertEquals(Money.of("400.00", Currency.SAR), overview.remaining)
        assertEquals(1, overview.rows.count { it.status == CommitmentPaymentStatus.PAID })
    }

    @Test
    fun refundDoesNotCountAsRecurringCommitmentPayment() {
        val refund = transaction(
            id = "refund-stc",
            merchant = "STC",
            amount = "173",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
            type = FinancialTransactionType.REFUND,
        )
        val commitment = commitment(
            name = "STC",
            amount = Money.of("173.00", Currency.SAR),
            sourceTransactionId = "tx-stc",
            recurrence = CommitmentRecurrence.MONTHLY,
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(emptyList(), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(refund),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
    }

    @Test
    fun paidCreditCardStatement_isMarkedPaid() {
        val facility = creditFacility(
            due = Money.of("500.00", Currency.SAR),
            last4 = "1234",
        )
        val payment = transaction(
            id = "payment-card",
            merchant = "Card payment",
            amount = "500",
            at = FinancialPeriodPolicy.toInclusiveStartInstant(period.startDate, zone).plusSeconds(60),
            type = FinancialTransactionType.CREDIT_CARD_PAYMENT,
            destinationContainerId = "card:${Bank.BANK_ALJAZIRA.id}:1234",
        )

        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(listOf(facility), emptyList(), Currency.SAR),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = listOf(payment),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("500.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun inactiveCommitments_areExcluded() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(
                commitment(
                    name = "School",
                    amount = Money.of("3000.00", Currency.SAR),
                    sourceTransactionId = "tx-school",
                    active = false,
                ),
            ),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertTrue(overview.rows.isEmpty())
        assertEquals(Money.zero(Currency.SAR), overview.total)
    }

    @Test
    fun loanWithPeriodPayment_countsAsPaid() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(
                loans = listOf(
                    LoanOverview(
                        bank = Bank.BANK_ALJAZIRA,
                        loanType = LoanType.PERSONAL,
                        displayLabel = "Personal loan",
                        remainingBalance = Money.of("12000.00", Currency.SAR),
                        remainingBalanceAsOf = Instant.parse("2026-08-01T00:00:00Z"),
                        salaryPeriodPayment = SignedMoneyAmount.of(Money.of("900.00", Currency.SAR)),
                        salaryPeriodLabel = "27 July",
                    ),
                ),
                salaryPeriodLabel = "27 July",
                currency = Currency.SAR,
            ),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.PAID, overview.rows.single().status)
        assertEquals(Money.of("900.00", Currency.SAR), overview.total)
        assertEquals(Money.of("900.00", Currency.SAR), overview.paid)
        assertEquals(Money.zero(Currency.SAR), overview.remaining)
    }

    @Test
    fun loanWithoutPeriodPayment_usesInstallmentNotRemainingBalance() {
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = emptyList(),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(
                loans = listOf(
                    LoanOverview(
                        bank = Bank.BANK_ALJAZIRA,
                        loanType = LoanType.PERSONAL,
                        displayLabel = "Personal loan",
                        remainingBalance = Money.of("12000.00", Currency.SAR),
                        remainingBalanceAsOf = Instant.parse("2026-08-01T00:00:00Z"),
                        latestInstallmentAmount = Money.of("900.00", Currency.SAR),
                        latestInstallmentAsOf = Instant.parse("2026-07-27T00:00:00Z"),
                        salaryPeriodPayment = SignedMoneyAmount.zero(Currency.SAR),
                        salaryPeriodLabel = "27 July",
                    ),
                ),
                salaryPeriodLabel = "27 July",
                currency = Currency.SAR,
            ),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = emptyMap(),
            zoneId = zone,
        )

        assertEquals(CommitmentPaymentStatus.UNPAID, overview.rows.single().status)
        assertEquals(Money.of("900.00", Currency.SAR), overview.total)
        assertEquals(Money.of("900.00", Currency.SAR), overview.remaining)
    }

    @Test
    fun foreignCurrencyCommitment_usesSarEquivalentWhenSourceTxMissingFromPeriodList() {
        val commitment = commitment(
            name = "Amazon",
            amount = Money.of("25.00", Currency.USD),
            sourceTransactionId = "tx-usd",
            recurrence = CommitmentRecurrence.MONTHLY,
        )
        val overview = CommitmentsOverviewBuilder.build(
            salaryPeriod = period,
            commitments = listOf(commitment),
            creditFacilities = CreditFacilitiesOverview(
                facilities = emptyList(),
                debitCards = emptyList(),
                currency = Currency.SAR,
            ),
            loansOverview = LoansOverview(emptyList(), null, Currency.SAR),
            transactions = emptyList(),
            primaryCurrency = Currency.SAR,
            sarEquivalents = mapOf("tx-usd" to Money.of("93.75", Currency.SAR)),
            zoneId = zone,
        )

        assertEquals(Money.of("93.75", Currency.SAR), overview.rows.single().amount)
        assertEquals(Money.of("93.75", Currency.SAR), overview.total)
    }

    private fun commitment(
        name: String,
        amount: Money,
        sourceTransactionId: String,
        recurrence: CommitmentRecurrence? = null,
        active: Boolean = true,
        transactionDate: LocalDate = LocalDate.parse("2026-08-01"),
    ): Commitment {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        return Commitment(
            id = RegistryEntityIdFactory.newCommitmentId(),
            name = name,
            amount = amount,
            transactionDate = transactionDate,
            recurrence = recurrence,
            dueDate = null,
            active = active,
            sourceTransactionId = sourceTransactionId,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun transaction(
        id: String,
        merchant: String,
        amount: String,
        at: Instant,
        type: FinancialTransactionType = FinancialTransactionType.EXPENSE,
        destinationContainerId: String? = null,
    ): FinancialTransaction =
        FinancialTransaction(
            id = id,
            type = type,
            amount = Money.of(amount, Currency.SAR),
            occurredAt = at,
            sourceContainerId = null,
            destinationContainerId = destinationContainerId,
            merchant = merchant,
            counterparty = null,
            categoryId = null,
            linkedParsedEventIds = emptyList(),
        )

    private fun creditFacility(
        due: Money,
        last4: String,
    ): CreditFacilityOverview =
        CreditFacilityOverview(
            bank = Bank.BANK_ALJAZIRA,
            primary = CreditCardDashboardRow(
                bank = Bank.BANK_ALJAZIRA,
                last4 = last4,
                calendarMonthSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
                statementSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
                salaryPeriodSpendingNet = SignedMoneyAmount.zero(Currency.SAR),
                statementPeriodLabel = null,
                snapshot = null,
            ),
            supplementaries = emptyList(),
            facilityDue = StatementDueSnapshot(
                amount = due,
                updatedAt = Instant.parse("2026-07-25T00:00:00Z"),
                dueDate = LocalDate.parse("2026-08-15"),
            ),
            facilitySalaryPeriodSpending = SignedMoneyAmount.zero(Currency.SAR),
            facilityStatementSpending = SignedMoneyAmount.zero(Currency.SAR),
            aggregateStatementPeriodLabel = null,
            salaryPeriodLabel = null,
            currency = Currency.SAR,
        )
}
