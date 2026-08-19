package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.bank.aljazira.extraction.MoneyTokens
import com.baraa.masroof.core.money.Currency
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

/**
 * Merchant + currency → exchange-rate evidence mined from earlier SMS that included سعر الصرف.
 */
class HistoricalExchangeRateIndex private constructor(
    private val ratesByMerchant: Map<String, Map<Currency, BigDecimal>>,
) {
    fun rateForMerchant(merchant: String?, currency: Currency): BigDecimal? {
        if (!currency.convertsToSar()) return null
        val normalized = merchant?.normalizeMerchantKey() ?: return null
        val byCurrency = ratesByMerchant[normalized]
            ?: ratesByMerchant.entries.firstOrNull { (key, _) ->
                normalized.contains(key) || key.contains(normalized)
            }?.value
            ?: return null
        return byCurrency[currency]
    }

    companion object {
        fun build(
            parsedRecords: List<ParsedEventRecord>,
            rawSmsById: Map<String, RawSms>,
        ): HistoricalExchangeRateIndex {
            val latest = mutableMapOf<String, MutableMap<Currency, Pair<BigDecimal, Instant?>>>()
            for (record in parsedRecords) {
                val merchant = record.event.merchant?.normalizeMerchantKey() ?: continue
                val body = rawSmsById[record.event.rawSmsId]?.body ?: continue
                val currency = record.event.amount?.currency?.takeIf { it.convertsToSar() }
                    ?: foreignCurrencyFromSms(body)
                    ?: continue
                val rate = InternationalPurchaseFactsExtractor.extract(body)?.exchangeRate ?: continue
                val occurredAt = record.details.occurredAtLocal
                    ?.atZone(java.time.ZoneId.systemDefault())
                    ?.toInstant()
                    ?: record.event.occurredAt
                val merchantRates = latest.getOrPut(merchant) { mutableMapOf() }
                val existing = merchantRates[currency]
                if (existing == null || isNewer(occurredAt, existing.second)) {
                    merchantRates[currency] = rate to occurredAt
                }
            }
            return HistoricalExchangeRateIndex(
                latest.mapValues { (_, byCurrency) ->
                    byCurrency.mapValues { it.value.first }
                },
            )
        }

        private fun isNewer(candidate: Instant?, current: Instant?): Boolean {
            if (candidate == null) return current == null
            if (current == null) return true
            return candidate.isAfter(current)
        }

        private fun String.normalizeMerchantKey(): String =
            lowercase(Locale.ROOT).trim()

        private fun foreignCurrencyFromSms(body: String): Currency? {
            val match = MoneyTokens.moneyAfterLabel.find(body) ?: return null
            val money = MoneyTokens.parseMoneyFromMatch(match) ?: return null
            return money.currency.takeIf { it.convertsToSar() }
        }
    }
}
