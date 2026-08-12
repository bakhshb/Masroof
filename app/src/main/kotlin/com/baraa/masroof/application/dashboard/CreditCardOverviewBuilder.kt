package com.baraa.masroof.application.dashboard

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
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the primary credit-card dashboard row from statement SMS and card transactions
 * since the latest statement (or the current statement-cycle start on the 10th).
 */
object CreditCardOverviewBuilder {
    private val dueDateExtractor = DueDateExtractor()
    private val statementDayMonth: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM", Locale("ar"))

    fun resolveStatementSpendingStart(
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        zoneId: ZoneId,
        clock: Clock,
    ): Instant {
        val latestStatement = latestPrimaryStatement(parsedRecords, rawSmsById)
        if (latestStatement != null) {
            return latestStatement.updatedAt
        }
        val anchor = LocalDate.now(clock)
        val cycleStart = CreditCardStatementPolicy.statementCycleStartOnOrBefore(anchor)
        return cycleStart.atStartOfDay(zoneId).toInstant()
    }

    fun build(
        statementTransactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        zoneId: ZoneId,
        clock: Clock,
        primaryCurrency: Currency = Currency.SAR,
        sarEquivalents: Map<String, Money> = emptyMap(),
        statementPeriodLabelFormatter: (Instant, ZoneId) -> String = { instant, zone ->
            statementDayMonth.format(instant.atZone(zone).toLocalDate())
        },
    ): CreditCardsOverview {
        val primaryLast4 = CreditCardStatementPolicy.PRIMARY_CARD_LAST4
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
            val dueDate = dueDateExtractor.extractFromText(raw.body)
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
                    dueDate = dueDate,
                )
            }
        }

        val primaryCandidates = snapshotCandidates.filter { it.cardRef.last4 == primaryLast4 }
        val latestStatement = primaryCandidates
            .filter { it.isStatement }
            .maxByOrNull { it.updatedAt }
        val latestAvailable = primaryCandidates
            .filter { !it.isStatement && it.details.availableBalance != null }
            .maxByOrNull { it.updatedAt }

        val statementStart = latestStatement?.updatedAt
            ?: resolveStatementSpendingStart(parsedRecords, rawSmsById, zoneId, clock)
        val statementPeriodLabel = statementPeriodLabelFormatter(statementStart, zoneId)

        val spendingGross = mutableMapOf<String, Money>()
        val refunds = mutableMapOf<String, Money>()
        for (tx in statementTransactions) {
            if (tx.occurredAt.isBefore(statementStart)) continue
            val amount = when {
                tx.amount.currency == primaryCurrency -> tx.amount
                else -> sarEquivalents[tx.id] ?: continue
            }
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.FEE,
                -> {
                    val cardId = tx.sourceContainerId ?: continue
                    if (cardId !in creditCardMeta) continue
                    spendingGross[cardId] = (spendingGross[cardId] ?: Money.zero(primaryCurrency)) + amount
                }

                FinancialTransactionType.REFUND -> {
                    val cardId = tx.destinationContainerId ?: continue
                    if (cardId !in creditCardMeta) continue
                    refunds[cardId] = (refunds[cardId] ?: Money.zero(primaryCurrency)) + amount
                }

                else -> Unit
            }
        }

        val primaryCardId = creditCardMeta.entries
            .firstOrNull { it.value.last4 == primaryLast4 }
            ?.key

        val primarySnapshot = buildPrimarySnapshot(latestStatement, latestAvailable)
        val primarySpending = if (primaryCardId != null) {
            val gross = spendingGross[primaryCardId] ?: Money.zero(primaryCurrency)
            val refund = refunds[primaryCardId] ?: Money.zero(primaryCurrency)
            SignedMoneyAmount.difference(gross, refund)
        } else {
            SignedMoneyAmount.zero(primaryCurrency)
        }

        val primaryRow = if (
            primarySnapshot != null ||
            latestStatement != null ||
            primarySpending.amount.signum() != 0
        ) {
            val ref = creditCardMeta[primaryCardId]
                ?: latestStatement?.cardRef
                ?: latestAvailable?.cardRef
                ?: CardReference(Bank.BANK_ALJAZIRA, primaryLast4)
            CreditCardDashboardRow(
                bank = ref.bank,
                last4 = ref.last4.orEmpty().ifEmpty { primaryLast4 },
                isPrimary = true,
                statementSpendingNet = primarySpending,
                snapshot = primarySnapshot,
            )
        } else {
            null
        }

        val supplementaryCardCount = creditCardMeta.values
            .mapNotNull { it.last4 }
            .distinct()
            .count { it != primaryLast4 }

        return CreditCardsOverview(
            cards = listOfNotNull(primaryRow),
            aggregateDueAmount = latestStatement?.details?.outstandingBalance,
            aggregateDueUpdatedAt = latestStatement?.updatedAt,
            aggregateDueDate = latestStatement?.dueDate,
            statementPeriodLabel = statementPeriodLabel,
            supplementaryCardCount = supplementaryCardCount,
            currency = primaryCurrency,
        )
    }

    private fun buildPrimarySnapshot(
        latestStatement: SnapshotCandidate?,
        latestAvailable: SnapshotCandidate?,
    ): CreditCardBalanceSnapshot? {
        if (latestStatement == null && latestAvailable == null) return null
        val updatedAt = listOfNotNull(latestStatement?.updatedAt, latestAvailable?.updatedAt)
            .maxOrNull()
            ?: return null
        return CreditCardBalanceSnapshot(
            availableBalance = latestAvailable?.details?.availableBalance,
            dueAmount = latestStatement?.details?.outstandingBalance,
            dueDate = latestStatement?.dueDate,
            statementIssuedAt = latestStatement?.updatedAt,
            updatedAt = updatedAt,
        )
    }

    private fun latestPrimaryStatement(
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): SnapshotCandidate? {
        val primaryLast4 = CreditCardStatementPolicy.PRIMARY_CARD_LAST4
        return parsedRecords.mapNotNull { record ->
            val cardRef = record.event.cardRef ?: return@mapNotNull null
            if (cardRef.last4 != primaryLast4) return@mapNotNull null
            val raw = rawSmsById[record.event.rawSmsId] ?: return@mapNotNull null
            if (!CreditCardStatementHeuristics.isStatementSms(raw.body)) return@mapNotNull null
            val cardId = FinancialContainerIdFactory.cardId(cardRef) ?: return@mapNotNull null
            SnapshotCandidate(
                cardId = cardId,
                cardRef = cardRef,
                updatedAt = record.event.occurredAt ?: raw.receivedAt,
                details = record.details,
                isStatement = true,
                dueDate = dueDateExtractor.extractFromText(raw.body),
            )
        }.maxByOrNull { it.updatedAt }
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
