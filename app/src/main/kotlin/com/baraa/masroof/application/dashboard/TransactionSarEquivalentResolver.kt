package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.ExchangeRateSource
import com.baraa.masroof.domain.model.FinancialTransaction
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.time.ZoneId

class TransactionSarEquivalentResolver(
    private val marketRateProvider: ForeignSarMarketRateProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun resolve(
        transactions: List<FinancialTransaction>,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
        primaryCurrency: Currency = Currency.SAR,
    ): Map<String, SarEquivalentResolution> {
        val parsedByEventId = parsedRecords.associateBy { it.event.id }
        val rateIndex = HistoricalExchangeRateIndex.build(parsedRecords, rawSmsById)
        val result = mutableMapOf<String, SarEquivalentResolution>()
        for (tx in transactions) {
            if (tx.amount.currency == primaryCurrency) continue
            if (!tx.amount.currency.convertsToSar()) continue

            val rawSmsBody = linkedRawSmsBody(tx, parsedByEventId, rawSmsById)
            val includeFee = tx.type != FinancialTransactionType.REFUND

            if (tx.appliedExchangeRate != null) {
                resolutionFromRate(
                    tx = tx,
                    exchangeRate = tx.appliedExchangeRate,
                    source = tx.exchangeRateSource ?: ExchangeRateSource.SMS,
                    primaryCurrency = primaryCurrency,
                    rawSmsBody = rawSmsBody,
                    includeFee = includeFee,
                )?.let { result[tx.id] = it }
                continue
            }

            if (rawSmsBody == null) continue

            val smsFacts = InternationalPurchaseFactsExtractor.extract(rawSmsBody)
            if (smsFacts != null) {
                ForeignPurchaseSarConverter.foreignToSar(
                    foreignAmount = tx.amount,
                    exchangeRate = smsFacts.exchangeRate,
                    internationalFee = if (includeFee) smsFacts.internationalFee else null,
                    targetCurrency = primaryCurrency,
                )?.let { sar ->
                    result[tx.id] = SarEquivalentResolution(
                        sarAmount = sar,
                        exchangeRate = smsFacts.exchangeRate,
                        source = ExchangeRateSource.SMS,
                    )
                }
                continue
            }

            val merchant = tx.merchant
                ?: tx.linkedParsedEventIds.firstNotNullOfOrNull { parsedByEventId[it]?.event?.merchant }
            val historicalRate = rateIndex.rateForMerchant(merchant, tx.amount.currency)
            if (historicalRate != null) {
                ForeignPurchaseSarConverter.foreignToSar(
                    foreignAmount = tx.amount,
                    exchangeRate = historicalRate,
                    internationalFee = null,
                    targetCurrency = primaryCurrency,
                )?.let { sar ->
                    result[tx.id] = SarEquivalentResolution(
                        sarAmount = sar,
                        exchangeRate = historicalRate,
                        source = ExchangeRateSource.HISTORICAL_MERCHANT,
                    )
                }
                continue
            }

            val onDate = tx.occurredAt.atZone(zoneId).toLocalDate()
            val marketRate = marketRateProvider.rateFor(tx.amount.currency, onDate) ?: continue
            ForeignPurchaseSarConverter.foreignToSar(
                foreignAmount = tx.amount,
                exchangeRate = marketRate,
                internationalFee = null,
                targetCurrency = primaryCurrency,
            )?.let { sar ->
                result[tx.id] = SarEquivalentResolution(
                    sarAmount = sar,
                    exchangeRate = marketRate,
                    source = ExchangeRateSource.MARKET,
                )
            }
        }
        return result
    }

    private fun linkedRawSmsBody(
        tx: FinancialTransaction,
        parsedByEventId: Map<String, ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): String? {
        val rawSmsId = tx.linkedParsedEventIds.firstOrNull()?.let { eventId ->
            parsedByEventId[eventId]?.event?.rawSmsId
        } ?: return null
        return rawSmsById[rawSmsId]?.body
    }

    private fun resolutionFromRate(
        tx: FinancialTransaction,
        exchangeRate: java.math.BigDecimal,
        source: ExchangeRateSource,
        primaryCurrency: Currency,
        rawSmsBody: String?,
        includeFee: Boolean,
    ): SarEquivalentResolution? {
        val internationalFee = if (includeFee && source == ExchangeRateSource.SMS && rawSmsBody != null) {
            InternationalPurchaseFactsExtractor.extract(rawSmsBody)?.internationalFee
        } else {
            null
        }
        val sar = ForeignPurchaseSarConverter.foreignToSar(
            foreignAmount = tx.amount,
            exchangeRate = exchangeRate,
            internationalFee = internationalFee,
            targetCurrency = primaryCurrency,
        ) ?: return null
        return SarEquivalentResolution(
            sarAmount = sar,
            exchangeRate = exchangeRate,
            source = source,
        )
    }
}

fun Map<String, SarEquivalentResolution>.sarAmounts(): Map<String, Money> =
    mapValues { (_, resolution) -> resolution.sarAmount }
