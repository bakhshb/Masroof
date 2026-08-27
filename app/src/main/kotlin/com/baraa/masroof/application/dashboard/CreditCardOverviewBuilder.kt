package com.baraa.masroof.application.dashboard

import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.bank.aljazira.CreditCardMessageHeuristics
import com.baraa.masroof.bank.aljazira.CreditCardStatementHeuristics
import com.baraa.masroof.bank.aljazira.extraction.DueDateExtractor
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.domain.period.CreditCardStatementPolicy
import com.baraa.masroof.domain.period.FinancialPeriod
import com.baraa.masroof.domain.period.FinancialPeriodPolicy
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds all credit-card rows with statement-cycle and salary-period spending per card.
 */
object CreditCardOverviewBuilder {
    private val dueDateExtractor = DueDateExtractor()

    private fun dayMonthFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM", locale)

    fun resolveStatementSpendingStart(
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        zoneId: ZoneId,
        periodEndExclusive: Instant,
    ): Instant = resolveGlobalStatementStart(
        parsedRecords = parsedRecords,
        rawSmsById = rawSmsById,
        zoneId = zoneId,
        periodEndExclusive = periodEndExclusive,
    )

    fun build(
        salaryPeriod: FinancialPeriod,
        cardTransactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        zoneId: ZoneId,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        displayLocale: Locale = Locale.forLanguageTag(AppLocale.TAG_AR),
    ): CreditCardsOverview {
        val dayMonth = dayMonthFormatter(displayLocale)
        val creditCardMeta = mutableMapOf<String, CardReference>()
        val snapshotCandidates = mutableListOf<SnapshotCandidate>()

        for (record in parsedRecords) {
            val event = record.event
            val cardRef = event.cardRef ?: continue
            val cardId = FinancialContainerIdFactory.cardId(cardRef) ?: continue
            val raw = rawSmsById[event.rawSmsId] ?: continue
            if (!CreditCardMessageHeuristics.isCreditCardSms(raw.body)) continue

            creditCardMeta[cardId] = cardRef
            val at = event.occurredAt ?: raw.receivedAt
            val details = record.details
            val isStatement = CreditCardStatementHeuristics.isStatementSms(raw.body)
            if (
                isStatement ||
                details.availableBalance != null ||
                details.outstandingBalance != null
            ) {
                snapshotCandidates += SnapshotCandidate(
                    cardId = cardId,
                    cardRef = cardRef,
                    updatedAt = at,
                    details = details,
                    isStatement = isStatement,
                    dueDate = dueDateExtractor.extractFromText(raw.body),
                )
            }
        }

        val periodEndExclusive = FinancialPeriodPolicy.toExclusiveEndInstant(
            salaryPeriod.endDateExclusive,
            zoneId,
        )
        val globalStatementStart = resolveGlobalStatementStart(
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
            zoneId = zoneId,
            periodEndExclusive = periodEndExclusive,
        )
        val salaryPeriodStart = FinancialPeriodPolicy.toInclusiveStartInstant(salaryPeriod.startDate, zoneId)
        val salaryPeriodLabel = dayMonth.format(salaryPeriod.startDate)
        val calendarMonthAnchor = salaryPeriod.displayEndDateInclusive.withDayOfMonth(1)
        val calendarMonthStart = calendarMonthAnchor.atStartOfDay(zoneId).toInstant()
        val calendarMonthLabel = dayMonth.format(calendarMonthAnchor)

        val latestStatementByCard = snapshotCandidates
            .filter { it.isStatement && it.updatedAt.isBefore(periodEndExclusive) }
            .groupBy { it.cardId }
            .mapValues { (_, candidates) -> candidates.maxBy { it.updatedAt } }

        val latestAvailableByCard = snapshotCandidates
            .filter {
                !it.isStatement &&
                    it.details.availableBalance != null &&
                    it.updatedAt.isBefore(periodEndExclusive)
            }
            .groupBy { it.cardId }
            .mapValues { (_, candidates) -> candidates.maxBy { it.updatedAt } }

        val cardIds = creditCardMeta.keys.toSet()

        val rows = cardIds.map { cardId ->
            val ref = creditCardMeta[cardId]
                ?: latestStatementByCard[cardId]?.cardRef
                ?: latestAvailableByCard[cardId]?.cardRef
                ?: parseCardId(cardId)
            val cardStatementStart = latestStatementByCard[cardId]?.updatedAt ?: globalStatementStart
            val statementLabel = dayMonth.format(cardStatementStart.atZone(zoneId).toLocalDate())

            val statementNet = netSpending(
                transactions = cardTransactions,
                cardId = cardId,
                startInclusive = cardStatementStart,
                endExclusive = periodEndExclusive,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            )
            val salaryNet = netSpending(
                transactions = cardTransactions,
                cardId = cardId,
                startInclusive = salaryPeriodStart,
                endExclusive = periodEndExclusive,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            )
            val calendarMonthNet = netSpending(
                transactions = cardTransactions,
                cardId = cardId,
                startInclusive = calendarMonthStart,
                endExclusive = periodEndExclusive,
                primaryCurrency = primaryCurrency,
                sarEquivalents = sarEquivalents,
            )

            CreditCardDashboardRow(
                bank = ref.bank,
                last4 = ref.last4.orEmpty(),
                calendarMonthSpendingNet = calendarMonthNet,
                statementSpendingNet = statementNet,
                salaryPeriodSpendingNet = salaryNet,
                statementPeriodLabel = statementLabel,
                snapshot = buildCardSnapshot(
                    latestAvailable = latestAvailableByCard[cardId],
                    latestStatement = latestStatementByCard[cardId],
                ),
            )
        }.sortedBy { it.last4 }

        val latestStatement = snapshotCandidates
            .filter { it.isStatement && it.updatedAt.isBefore(periodEndExclusive) }
            .maxByOrNull { it.updatedAt }

        val aggregatePeriodSpending = sumSpending(rows) { it.salaryPeriodSpendingNet }
        val aggregateStatementSpending = sumSpending(rows) { it.statementSpendingNet }
        val aggregateStatementLabel = latestStatement?.updatedAt?.let {
            dayMonth.format(it.atZone(zoneId).toLocalDate())
        } ?: dayMonth.format(globalStatementStart.atZone(zoneId).toLocalDate())
        val statementDue = resolveLatestStatementDue(rows)

        return CreditCardsOverview(
            cards = rows,
            aggregateDueAmount = statementDue?.amount,
            aggregateDueUpdatedAt = statementDue?.updatedAt,
            aggregateDueDate = statementDue?.dueDate,
            aggregatePeriodSpendingNet = aggregatePeriodSpending,
            aggregateStatementSpendingNet = aggregateStatementSpending,
            aggregateStatementPeriodLabel = aggregateStatementLabel,
            calendarMonthLabel = calendarMonthLabel,
            salaryPeriodLabel = salaryPeriodLabel,
            currency = primaryCurrency,
        )
    }

    private fun sumSpending(
        rows: List<CreditCardDashboardRow>,
        selector: (CreditCardDashboardRow) -> SignedMoneyAmount,
    ): SignedMoneyAmount {
        if (rows.isEmpty()) return SignedMoneyAmount.zero(Currency.SAR)
        var sum = java.math.BigDecimal.ZERO
        val currency = rows.first().statementSpendingNet.currency
        for (row in rows) {
            sum = sum.add(selector(row).amount)
        }
        return SignedMoneyAmount(
            sum.setScale(Money.SCALE, java.math.RoundingMode.HALF_EVEN),
            currency,
        )
    }

    private fun resolveGlobalStatementStart(
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        zoneId: ZoneId,
        periodEndExclusive: Instant,
    ): Instant {
        val latestStatement = parsedRecords.mapNotNull { record ->
            val raw = rawSmsById[record.event.rawSmsId] ?: return@mapNotNull null
            if (!CreditCardStatementHeuristics.isStatementSms(raw.body)) return@mapNotNull null
            val at = record.event.occurredAt ?: raw.receivedAt
            if (!at.isBefore(periodEndExclusive)) return@mapNotNull null
            at
        }.maxOrNull()
        if (latestStatement != null) return latestStatement

        val anchor = periodEndExclusive.atZone(zoneId).toLocalDate().minusDays(1)
        val cycleStart = CreditCardStatementPolicy.statementCycleStartOnOrBefore(anchor)
        return cycleStart.atStartOfDay(zoneId).toInstant()
    }

    private fun netSpending(
        transactions: List<FinancialTransaction>,
        cardId: String,
        startInclusive: Instant,
        endExclusive: Instant,
        primaryCurrency: Currency,
        sarEquivalents: Map<String, Money>,
    ): SignedMoneyAmount {
        var gross = Money.zero(primaryCurrency)
        var refund = Money.zero(primaryCurrency)
        for (tx in transactions) {
            if (tx.occurredAt.isBefore(startInclusive)) continue
            if (!tx.occurredAt.isBefore(endExclusive)) continue
            val amount = when {
                tx.amount.currency == primaryCurrency -> tx.amount
                else -> sarEquivalents[tx.id] ?: continue
            }
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.FEE,
                -> {
                    if (tx.sourceContainerId != cardId) continue
                    gross += amount
                }

                FinancialTransactionType.REFUND -> {
                    if (tx.destinationContainerId != cardId) continue
                    refund += amount
                }

                else -> Unit
            }
        }
        return SignedMoneyAmount.difference(gross, refund)
    }

    private fun buildCardSnapshot(
        latestAvailable: SnapshotCandidate?,
        latestStatement: SnapshotCandidate?,
    ): CreditCardBalanceSnapshot? {
        if (latestAvailable == null && latestStatement == null) return null

        val updatedAt = listOfNotNull(
            latestAvailable?.updatedAt,
            latestStatement?.updatedAt,
        ).maxOrNull() ?: return null

        return CreditCardBalanceSnapshot(
            availableBalance = latestAvailable?.details?.availableBalance,
            // Statement SMS only — purchase/refund "إجمالي المبلغ المستحق" is account-level
            // and repeats across linked cards on the same credit facility.
            dueAmount = latestStatement?.details?.outstandingBalance,
            dueDate = latestStatement?.dueDate,
            statementIssuedAt = latestStatement?.updatedAt,
            updatedAt = updatedAt,
        )
    }

    private fun parseCardId(cardId: String): CardReference {
        val parts = cardId.split(":")
        val last4 = parts.getOrNull(2).orEmpty()
        val bankId = parts.getOrNull(1).orEmpty()
        val bank = when (bankId) {
            Bank.BANK_ALJAZIRA.id -> Bank.BANK_ALJAZIRA
            Bank.UNKNOWN.id -> Bank.UNKNOWN
            else -> Bank(bankId)
        }
        return CardReference(bank = bank, last4 = last4)
    }

    private data class SnapshotCandidate(
        val cardId: String,
        val cardRef: CardReference,
        val updatedAt: Instant,
        val details: ParsedEventDetails,
        val isStatement: Boolean,
        val dueDate: LocalDate?,
    )
}
