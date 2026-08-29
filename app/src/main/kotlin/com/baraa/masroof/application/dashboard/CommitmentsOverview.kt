package com.baraa.masroof.application.dashboard

import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.Commitment
import com.baraa.masroof.domain.model.CommitmentRecurrence
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.domain.repository.CommitmentRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

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
    val dueDate: LocalDate?,
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
        val rows = buildList {
            commitments.filter { it.active }.forEach { commitment ->
                if (
                    commitment.recurrence == null &&
                    !isOneTimeCommitmentInPeriod(commitment, salaryPeriod, transactions, zoneId)
                ) {
                    return@forEach
                }
                buildUserRow(
                    commitment,
                    salaryPeriod,
                    transactions,
                    primaryCurrency,
                    sarEquivalents,
                    zoneId,
                )?.let(::add)
            }
            addAll(buildCreditCardRows(creditFacilities))
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

    private fun buildUserRow(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
    ): CommitmentDashboardRow? {
        val amount = resolveMoney(
            amount = commitment.amount,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            sourceTransactionId = commitment.sourceTransactionId,
            transactions = transactions,
        ) ?: return null
        val status = resolveUserStatus(
            commitment = commitment,
            salaryPeriod = salaryPeriod,
            transactions = transactions,
            primaryCurrency = primaryCurrency,
            sarEquivalents = sarEquivalents,
            zoneId = zoneId,
        )
        return CommitmentDashboardRow(
            key = "user:${commitment.id}",
            source = CommitmentDashboardSource.USER,
            displayName = commitment.name,
            amount = amount,
            dueDate = commitment.dueDate,
            status = status,
            userCommitmentId = commitment.id,
        )
    }

    private fun buildCreditCardRows(
        creditFacilities: CreditFacilitiesOverview,
    ): List<CommitmentDashboardRow> =
        creditFacilities.facilities.mapNotNull { facility ->
            val due = facility.facilityDue ?: return@mapNotNull null
            CommitmentDashboardRow(
                key = "credit:${facility.bank.id}:${facility.primaryLast4}",
                source = CommitmentDashboardSource.CREDIT_CARD,
                displayName = "Credit ••${facility.primaryLast4}",
                amount = due.amount,
                dueDate = due.dueDate,
                status = CommitmentPaymentStatus.UNPAID,
                creditFacilityKey = "${facility.bank.id}:${facility.primaryLast4}",
            )
        }

    private fun buildLoanRows(
        loansOverview: LoansOverview,
    ): List<CommitmentDashboardRow> =
        loansOverview.loans.mapNotNull { loan ->
            val paid = loan.salaryPeriodPayment.amount.signum() > 0
            val amount = when {
                paid -> Money(loan.salaryPeriodPayment.amount, loan.salaryPeriodPayment.currency)
                loan.remainingBalance != null -> loan.remainingBalance
                else -> return@mapNotNull null
            }
            CommitmentDashboardRow(
                key = "loan:${loan.bank.id}:${loan.loanType.name}",
                source = CommitmentDashboardSource.LOAN,
                displayName = loan.displayLabel,
                amount = amount,
                dueDate = null,
                status = if (paid) CommitmentPaymentStatus.PAID else CommitmentPaymentStatus.UNPAID,
                loanKey = "${loan.bank.id}:${loan.loanType.name}",
            )
        }

    internal fun isOneTimeCommitmentInPeriod(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        zoneId: ZoneId,
    ): Boolean {
        val anchorDate = transactions
            .find { it.id == commitment.sourceTransactionId }
            ?.occurredAt
            ?.atZone(zoneId)
            ?.toLocalDate()
            ?: commitment.transactionDate
        return !anchorDate.isBefore(salaryPeriod.startDate) &&
            anchorDate.isBefore(salaryPeriod.endDateExclusive)
    }

    internal fun resolveUserStatus(
        commitment: Commitment,
        salaryPeriod: FinancialPeriod,
        transactions: List<FinancialTransaction>,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
        zoneId: ZoneId,
    ): CommitmentPaymentStatus {
        if (commitment.recurrence == null) {
            return CommitmentPaymentStatus.PAID
        }
        val periodStart = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zoneId)
        val periodEnd = FinancialPeriodPolicy.toExclusiveEndInstant(salaryPeriod.endDateExclusive, zoneId)
        val hasPayment = transactions.any { tx ->
            tx.occurredAt >= periodStart &&
                tx.occurredAt < periodEnd &&
                matchesCommitmentPayment(tx, commitment, primaryCurrency, sarEquivalents)
        }
        return if (hasPayment) CommitmentPaymentStatus.PAID else CommitmentPaymentStatus.UNPAID
    }

    internal fun matchesCommitmentPayment(
        transaction: FinancialTransaction,
        commitment: Commitment,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
    ): Boolean {
        if (transaction.id == commitment.sourceTransactionId) return true
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
            transactions = listOf(transaction),
        ) ?: return false
        if (txAmount.amount.compareTo(commitmentAmount.amount) != 0) return false
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
        transactions.find { it.id == sourceTransactionId }?.let { sourceTx ->
            TransactionAmountResolver.effectiveAmount(
                tx = sourceTx,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            )?.let { return it }
        }
        sarEquivalents[sourceTransactionId]?.let { return it }
        return if (amount.currency == primaryCurrency) amount else null
    }
}
