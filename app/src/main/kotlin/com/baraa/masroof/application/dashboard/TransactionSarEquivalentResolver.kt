package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.RoundingMode

/**
 * Estimates SAR impact for foreign-currency purchases using exchange rate from the SMS body.
 */
object ForeignPurchaseSarConverter {
    fun toSarEquivalent(
        foreignAmount: Money,
        rawSmsBody: String,
        targetCurrency: Currency = Currency.SAR,
    ): Money? {
        if (foreignAmount.currency == targetCurrency) return foreignAmount
        val facts = InternationalPurchaseFactsExtractor.extract(rawSmsBody) ?: return null
        if (foreignAmount.currency != Currency.USD || targetCurrency != Currency.SAR) return null

        val converted = foreignAmount.amount
            .multiply(facts.exchangeRate)
            .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        val fee = facts.internationalFee?.amount ?: java.math.BigDecimal.ZERO.setScale(Money.SCALE)
        val total = converted.add(fee).setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        return Money.of(total, Currency.SAR)
    }
}

object TransactionSarEquivalentResolver {
    fun resolve(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        primaryCurrency: Currency = Currency.SAR,
    ): Map<String, Money> {
        val parsedByEventId = parsedRecords.associateBy { it.event.id }
        val result = mutableMapOf<String, Money>()
        for (tx in transactions) {
            if (tx.amount.currency == primaryCurrency) continue
            val rawSmsId = tx.linkedParsedEventIds.firstOrNull()?.let { eventId ->
                parsedByEventId[eventId]?.event?.rawSmsId
            } ?: continue
            val body = rawSmsById[rawSmsId]?.body ?: continue
            val sar = ForeignPurchaseSarConverter.toSarEquivalent(tx.amount, body, primaryCurrency)
                ?: continue
            result[tx.id] = sar
        }
        return result
    }
}
