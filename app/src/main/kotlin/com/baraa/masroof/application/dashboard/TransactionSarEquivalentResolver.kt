package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimates SAR impact for foreign-currency purchases using exchange rate from the SMS body.
 */
object ForeignPurchaseSarConverter {
    fun toSarEquivalent(
        foreignAmount: Money,
        rawSmsBody: String,
        targetCurrency: Currency = Currency.SAR,
        includeInternationalFee: Boolean = true,
    ): Money? {
        if (foreignAmount.currency == targetCurrency) return foreignAmount
        val facts = InternationalPurchaseFactsExtractor.extract(rawSmsBody) ?: return null
        return usdToSar(
            foreignAmount = foreignAmount,
            exchangeRate = facts.exchangeRate,
            internationalFee = if (includeInternationalFee) facts.internationalFee else null,
            targetCurrency = targetCurrency,
        )
    }

    fun usdToSar(
        foreignAmount: Money,
        exchangeRate: BigDecimal,
        internationalFee: Money? = null,
        targetCurrency: Currency = Currency.SAR,
    ): Money? {
        if (foreignAmount.currency != Currency.USD || targetCurrency != Currency.SAR) return null
        val converted = foreignAmount.amount
            .multiply(exchangeRate)
            .setScale(Money.SCALE, RoundingMode.HALF_EVEN)
        val fee = internationalFee?.amount ?: java.math.BigDecimal.ZERO.setScale(Money.SCALE)
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
        val rateIndex = HistoricalExchangeRateIndex.build(parsedRecords, rawSmsById)
        val result = mutableMapOf<String, Money>()
        for (tx in transactions) {
            if (tx.amount.currency == primaryCurrency) continue
            val rawSmsId = tx.linkedParsedEventIds.firstOrNull()?.let { eventId ->
                parsedByEventId[eventId]?.event?.rawSmsId
            } ?: continue
            val body = rawSmsById[rawSmsId]?.body ?: continue
            val includeFee = tx.type != FinancialTransactionType.REFUND
            val direct = ForeignPurchaseSarConverter.toSarEquivalent(
                foreignAmount = tx.amount,
                rawSmsBody = body,
                targetCurrency = primaryCurrency,
                includeInternationalFee = includeFee,
            )
            if (direct != null) {
                result[tx.id] = direct
                continue
            }
            val merchant = tx.merchant
                ?: tx.linkedParsedEventIds.firstNotNullOfOrNull { parsedByEventId[it]?.event?.merchant }
            val historicalRate = rateIndex.rateForMerchant(merchant) ?: continue
            ForeignPurchaseSarConverter.usdToSar(
                foreignAmount = tx.amount,
                exchangeRate = historicalRate,
                internationalFee = null,
                targetCurrency = primaryCurrency,
            )?.let { result[tx.id] = it }
        }
        return result
    }
}
