package com.baraa.masroof.application.dashboard

import com.baraa.masroof.application.commitment.CommitmentPauseTransitions
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

enum class CommitmentPaymentStatus {
    PAID,
    UNPAID,
}

enum class CommitmentDashboardSource {
    USER,
    CREDIT_CARD,
    LOAN,
}

data class CommitmentDashboardRow(
    val key: String,
    val source: CommitmentDashboardSource,
    val displayName: String,
    val amount: Money,
    val expectedDate: LocalDate?,
    val status: CommitmentPaymentStatus,
    val userCommitmentId: String? = null,
    val creditFacilityKey: String? = null,
    val loanKey: String? = null,
)

data class CommitmentsOverview(
    val rows: List<CommitmentDashboardRow>,
    val total: Money,
    val paid: Money,
    val remaining: Money,
    val currency: Currency,
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    companion object {
        fun empty(currency: Currency = Currency.SAR): CommitmentsOverview {
            val zero = Money.zero(currency)
            return CommitmentsOverview(
                rows = emptyList(),
                total = zero,
                paid = zero,
                remaining = zero,
                currency = currency,
            )
        }
    }
}

object CommitmentsOverviewBuilder {
    fun build(
        salaryPeriod: FinancialPeriod,
        commitments: List<Commitment>,
        creditFacilities: CreditFacilitiesOverview,
        loansOverview: LoansOverview,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
    ): CommitmentsOverview {
        val consumedPaymentTransactionIds = mutableSetOf<String>()
        val rows = buildList {
            commitments
                .filter { isCommitmentVisibleInPeriod(it, salaryPeriod, zoneId) }
                .sortedBy { it.id }
                .forEach { commitment ->
                addAll(
                    buildUserRows(
                        commitment = commitment,
                        salaryPeriod = salaryPeriod,
                        transactions = transactions,
                        primaryCurrency = primaryCurrency,
                        sarEquivalents = sarEquivalents,
                        zoneId = zoneId,
                        consumedPaymentTransactionIds = consumedPaymentTransactionIds,
                    ),
                )
            }
            addAll(
                buildCreditCardRows(
                    creditFacilities = creditFacilities,
                    salaryPeriod = salaryPeriod,
                    transactions = transactions,
                    primaryCurrency = primaryCurrency,
                    sarEquivalents = sarEquivalents,
                    zoneId = zoneId,
                ),
            )
            addAll(buildLoanRows(loansOverview))
        }.sortedBy { it.displayName.lowercase() }

        if (rows.isEmpty()) {
            return CommitmentsOverview.empty(primaryCurrency)
        }

        var total = BigDecimal.ZERO.setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        var paid = BigDecimal.ZERO.setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        rows.forEach { row ->
            total = total.add(row.amount.amount)
            if (row.status == CommitmentPaymentStatus.PAID) {
                paid = paid.add(row.amount.amount)
            }
        }
        val remaining = total.subtract(paid).setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        val totalMoney = Money(total, primaryCurrency)
        val paidMoney = Money(paid, primaryCurrency)
        return CommitmentsOverview(
            rows = rows,
            total = totalMoney,
            paid = paidMoney,
            remaining = Money(remaining, primaryCurrency),
            currency = primaryCurrency,
        )
    }

    private fun buildUserRows(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
        consumedPaymentTransactionIds: MutableSet<String>,
    ): List<CommitmentDashboardRow> {
        val occurrences = occurrencesInPeriod(commitment, salaryPeriod)
        if (occurrences.isEmpty()) return emptyList()
        val amount = resolveMoney(
            amount = commitment.amount,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            sourceTransactionId = commitment.sourceTransactionId,
            transactions = transactions,
        ) ?: return emptyList()
        val matchedPayments = matchingPaymentCount(
            commitment = commitment,
            salaryPeriod = salaryPeriod,
            transactions = transactions,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            zoneId = zoneId,
            consumedPaymentTransactionIds = consumedPaymentTransactionIds,
        )
        val paidOccurrenceCount = if (commitment.recurrence == null) {
            when {
                occurrences.isEmpty() -> 0
                isDateInPeriod(commitment.transactionDate, salaryPeriod) || matchedPayments > 0 ->
                    occurrences.size
                else -> 0
            }
        } else {
            matchedPayments
        }
        return occurrences.mapIndexed { index, occurrence ->
            CommitmentDashboardRow(
                key = "user:${commitment.id}:$occurrence",
                source = CommitmentDashboardSource.USER,
                displayName = commitment.name,
                amount = amount,
                expectedDate = commitment.dueDate ?: occurrence,
                status = if (index < paidOccurrenceCount) {
                    CommitmentPaymentStatus.PAID
                } else {
                    CommitmentPaymentStatus.UNPAID
                },
                userCommitmentId = commitment.id,
            )
        }
    }

    private fun buildCreditCardRows(
        creditFacilities: CreditFacilitiesOverview,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
    ): List<CommitmentDashboardRow> =
        creditFacilities.facilities.mapNotNull { facility ->
            val due = facility.facilityDue ?: return@mapNotNull null
            if (!isStatementDueInPeriod(due, salaryPeriod, zoneId)) return@mapNotNull null
            val paid = hasSatisfiedFacilityDue(
                facility = facility,
                due = due,
                transactions = transactions,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            )
            CommitmentDashboardRow(
                key = "credit:${facility.bank.id}:${facility.primaryLast4}",
                source = CommitmentDashboardSource.CREDIT_CARD,
                displayName = facility.primaryLast4,
                amount = due.amount,
                expectedDate = due.dueDate,
                status = if (paid) CommitmentPaymentStatus.PAID else CommitmentPaymentStatus.UNPAID,
                creditFacilityKey = "${facility.bank.id}:${facility.primaryLast4}",
            )
        }

    internal fun isStatementDueInPeriod(
        due: StatementDueSnapshot,
        salaryPeriod: FinancialPeriod,
        zoneId: ZoneId,
    ): Boolean {
        val dueDate = due.dueDate ?: due.updatedAt.atZone(zoneId).toLocalDate()
        return isDateInPeriod(dueDate, salaryPeriod)
    }

    private fun hasSatisfiedFacilityDue(
        facility: CreditFacilityOverview,
        due: StatementDueSnapshot,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
    ): Boolean {
        val cardIds = facility.allCards.mapNotNull { card ->
            FinancialContainerIdFactory.cardId(card.bank, card.last4)
        }.toSet()
        val paid = transactions.fold(Money.zero(primaryCurrency)) { total, transaction ->
            if (
                transaction.type != FinancialTransactionType.CREDIT_CARD_PAYMENT ||
                transaction.destinationContainerId !in cardIds ||
                transaction.occurredAt.isBefore(due.updatedAt)
            ) {
                total
            } else {
                val amount = TransactionAmountResolver.effectiveAmount(
                    tx = transaction,
                    primaryCurrency = primaryCurrency,
                    sarEquivalents = sarEquivalents,
                )
                if (amount == null) total else total + amount
            }
        }
        return paid.amount >= due.amount.amount
    }

    private fun buildLoanRows(
        loansOverview: LoansOverview,
    ): List<CommitmentDashboardRow> =
        loansOverview.loans.mapNotNull { loan ->
            val paid = loan.salaryPeriodPayment.amount.signum() > 0
            if (!paid && loan.remainingBalance?.amount?.signum() == 0) {
                return@mapNotNull null
            }
            val amount = when {
                paid -> Money(loan.salaryPeriodPayment.amount, loan.salaryPeriodPayment.currency)
                loan.latestInstallmentAmount != null -> loan.latestInstallmentAmount
                else -> return@mapNotNull null
            }
            CommitmentDashboardRow(
                key = "loan:${loan.bank.id}:${loan.loanType.name}",
                source = CommitmentDashboardSource.LOAN,
                displayName = loan.displayLabel,
                amount = amount,
                expectedDate = null,
                status = if (paid) CommitmentPaymentStatus.PAID else CommitmentPaymentStatus.UNPAID,
                loanKey = "${loan.bank.id}:${loan.loanType.name}",
            )
        }

    internal fun isCommitmentVisibleInPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        zoneId: ZoneId,
    ): Boolean = CommitmentPauseTransitions.isVisibleInSalaryPeriod(commitment, salaryPeriod, zoneId)

    internal fun isOneTimeCommitmentInPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
    ): Boolean {
        return isDateInPeriod(commitment.transactionDate, salaryPeriod)
    }

    internal fun isCommitmentDueInPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
    ): Boolean = occurrencesInPeriod(commitment, salaryPeriod).isNotEmpty()

    internal fun occurrencesInPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
    ): List<LocalDate> {
        val anchor = commitment.transactionDate
        return when (commitment.recurrence) {
            null -> listOf(anchor).filter { isDateInPeriod(it, salaryPeriod) }
            CommitmentRecurrence.WEEKLY -> buildList {
                var occurrence = anchor
                while (occurrence.isBefore(salaryPeriod.startDate)) {
                    occurrence = occurrence.plusWeeks(1)
                }
                while (isDateInPeriod(occurrence, salaryPeriod)) {
                    add(occurrence)
                    occurrence = occurrence.plusWeeks(1)
                }
            }
            CommitmentRecurrence.MONTHLY -> occurrencesForMonths(
                anchor = anchor,
                start = salaryPeriod.startDate,
                endExclusive = salaryPeriod.endDateExclusive,
                monthStep = 1,
            )
            CommitmentRecurrence.YEARLY -> occurrencesForMonths(
                anchor = anchor,
                start = salaryPeriod.startDate,
                endExclusive = salaryPeriod.endDateExclusive,
                monthStep = 12,
            )
        }
    }

    private fun occurrencesForMonths(
        anchor: LocalDate,
        start: LocalDate,
        endExclusive: LocalDate,
        monthStep: Long,
    ): List<LocalDate> = buildList {
        var month = YearMonth.from(start)
        val endMonth = YearMonth.from(endExclusive.minusDays(1))
        while (!month.isAfter(endMonth)) {
            val monthOffset = (month.year - anchor.year) * 12L + month.monthValue - anchor.monthValue
            if (monthOffset >= 0 && monthOffset % monthStep == 0L) {
                val occurrence = month.atDay(minOf(anchor.dayOfMonth, month.lengthOfMonth()))
                if (isDateInPeriod(occurrence, FinancialPeriod(start, endExclusive))) {
                    add(occurrence)
                }
            }
            month = month.plusMonths(1)
        }
    }

    private fun isDateInPeriod(
        date: LocalDate,
        salaryPeriod: FinancialPeriod,
    ): Boolean {
        val anchorDate = date
        return !anchorDate.isBefore(salaryPeriod.startDate) &&
            anchorDate.isBefore(salaryPeriod.endDateExclusive)
    }

    private fun matchingPaymentCount(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
        consumedPaymentTransactionIds: MutableSet<String>,
    ): Int {
        val periodStart = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zoneId)
        val periodEnd = FinancialPeriodPolicy.toExclusiveEndInstant(salaryPeriod.endDateExclusive, zoneId)
        var matched = 0
        transactions.forEach { tx ->
            if (tx.occurredAt < periodStart || tx.occurredAt >= periodEnd) return@forEach
            if (tx.id in consumedPaymentTransactionIds) return@forEach
            if (
                !matchesCommitmentPayment(
                    transaction = tx,
                    commitment = commitment,
                    primaryCurrency = primaryCurrency,
                    sarEquivalents = sarEquivalents,
                    transactions = transactions,
                )
            ) {
                return@forEach
            }
            consumedPaymentTransactionIds.add(tx.id)
            matched++
        }
        return matched
    }

    internal fun matchesCommitmentPayment(
        transaction: FinancialTransaction,
        commitment: Commitment,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        transactions: List<FinancialTransaction>,
    ): Boolean {
        if (
            transaction.type != FinancialTransactionType.EXPENSE &&
            transaction.type != FinancialTransactionType.FEE &&
            transaction.type != FinancialTransactionType.BILL_PAYMENT
        ) {
            return false
        }
        val txAmount = TransactionAmountResolver.effectiveAmount(
            tx = transaction,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
        ) ?: return false
        val commitmentAmount = resolveMoney(
            amount = commitment.amount,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            sourceTransactionId = commitment.sourceTransactionId,
            transactions = transactions,
        ) ?: return false
        if (txAmount.amount.compareTo(commitmentAmount.amount) != 0) return false
        if (transaction.id == commitment.sourceTransactionId) return true
        val txName = transaction.merchant?.trim()?.lowercase()
            ?: transaction.counterparty?.trim()?.lowercase()
            ?: return false
        return txName == commitment.name.trim().lowercase()
    }

    private fun resolveMoney(
        amount: Money,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        sourceTransactionId: String,
        transactions: List<FinancialTransaction>,
    ): Money? {
        if (amount.currency == primaryCurrency) {
            return amount
        }
        val sourceTx = transactions.find { it.id == sourceTransactionId }
        if (sourceTx != null) {
            if (sourceTx.amount.currency != amount.currency) return null
            val sourceSar = TransactionAmountResolver.effectiveAmount(
                tx = sourceTx,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            ) ?: sarEquivalents[sourceTransactionId] ?: return null
            if (sourceTx.amount.amount.signum() == 0) return null
            val ratio = amount.amount.divide(
                sourceTx.amount.amount,
                Money.SCALE,
                RoundingMode.HALF_EVEN,
            )
            return Money(
                sourceSar.amount.multiply(ratio).setScale(Money.SCALE, RoundingMode.HALF_EVEN),
                primaryCurrency,
            )
        }
        return sarEquivalents[sourceTransactionId]
    }
}
