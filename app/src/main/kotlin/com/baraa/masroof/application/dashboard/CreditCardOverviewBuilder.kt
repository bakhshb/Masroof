package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.CreditCardMessageHeuristics
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.ids.FinancialContainerIdFactory
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.model.ParsedEventDetails
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.Instant

/**
 * Builds credit-card dashboard rows from parsed SMS snapshots and period transactions.
 */
object CreditCardOverviewBuilder {
    fun build(
        periodTransactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        primaryCurrency: Currency = Currency.SAR,
    ): CreditCardsOverview {
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
            if (details.availableBalance != null || details.outstandingBalance != null) {
                snapshotCandidates += SnapshotCandidate(
                    cardId = cardId,
                    cardRef = cardRef,
                    updatedAt = at,
                    details = details,
                )
            }
        }

        val spendingGross = mutableMapOf<String, Money>()
        val refunds = mutableMapOf<String, Money>()
        for (tx in periodTransactions) {
            if (tx.amount.currency != primaryCurrency) continue
            when (tx.type) {
                FinancialTransactionType.EXPENSE,
                FinancialTransactionType.FEE,
                -> {
                    val cardId = tx.sourceContainerId ?: continue
                    if (cardId !in creditCardMeta) continue
                    spendingGross[cardId] = (spendingGross[cardId] ?: Money.zero(primaryCurrency)) + tx.amount
                }

                FinancialTransactionType.REFUND -> {
                    val cardId = tx.destinationContainerId ?: continue
                    if (cardId !in creditCardMeta) continue
                    refunds[cardId] = (refunds[cardId] ?: Money.zero(primaryCurrency)) + tx.amount
                }

                else -> Unit
            }
        }

        val cardIds = (creditCardMeta.keys + spendingGross.keys + refunds.keys).toSet()
        val latestSnapshotByCard = snapshotCandidates
            .groupBy { it.cardId }
            .mapValues { (_, candidates) -> candidates.maxBy { it.updatedAt } }

        val aggregateDue = snapshotCandidates
            .mapNotNull { candidate ->
                candidate.details.outstandingBalance?.let { due ->
                    candidate.updatedAt to due
                }
            }
            .maxByOrNull { it.first }

        val rows = cardIds.map { cardId ->
            val ref = creditCardMeta[cardId]
                ?: latestSnapshotByCard[cardId]?.cardRef
                ?: parseCardId(cardId)
            val gross = spendingGross[cardId] ?: Money.zero(primaryCurrency)
            val refund = refunds[cardId] ?: Money.zero(primaryCurrency)
            val latest = latestSnapshotByCard[cardId]
            CreditCardDashboardRow(
                bank = ref.bank,
                last4 = ref.last4.orEmpty(),
                periodSpendingNet = SignedMoneyAmount.difference(gross, refund),
                snapshot = latest?.toSnapshot(),
            )
        }.sortedBy { it.last4 }

        return CreditCardsOverview(
            cards = rows,
            aggregateDueAmount = aggregateDue?.second,
            aggregateDueUpdatedAt = aggregateDue?.first,
            currency = primaryCurrency,
        )
    }

    private fun SnapshotCandidate.toSnapshot(): CreditCardBalanceSnapshot =
        CreditCardBalanceSnapshot(
            availableBalance = details.availableBalance,
            dueAmount = details.outstandingBalance,
            updatedAt = updatedAt,
        )

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
    )
}
