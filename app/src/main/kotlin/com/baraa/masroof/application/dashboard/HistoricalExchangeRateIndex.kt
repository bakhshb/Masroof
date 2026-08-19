package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.extraction.InternationalPurchaseFactsExtractor
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

/**
 * Merchant → exchange-rate evidence mined from earlier SMS that included سعر الصرف.
 */
class HistoricalExchangeRateIndex private constructor(
    private val ratesByMerchant: Map<String, BigDecimal>,
) {
    fun rateForMerchant(merchant: String?): BigDecimal? {
        val normalized = merchant?.normalizeMerchantKey() ?: return null
        ratesByMerchant[normalized]?.let { return it }
        return ratesByMerchant.entries.firstOrNull { (key, _) ->
            normalized.contains(key) || key.contains(normalized)
        }?.value
    }

    companion object {
        fun build(
            parsedRecords: List<ParsedEventRecord>,
            rawSmsById: Map<String, RawSms>,
        ): HistoricalExchangeRateIndex {
            val latest = mutableMapOf<String, Pair<BigDecimal, Instant?>>()
            for (record in parsedRecords) {
                val merchant = record.event.merchant?.normalizeMerchantKey() ?: continue
                val body = rawSmsById[record.event.rawSmsId]?.body ?: continue
                val rate = InternationalPurchaseFactsExtractor.extract(body)?.exchangeRate ?: continue
                val occurredAt = record.details.occurredAtLocal
                    ?.atZone(java.time.ZoneId.systemDefault())
                    ?.toInstant()
                    ?: record.event.occurredAt
                val existing = latest[merchant]
                if (existing == null || isNewer(occurredAt, existing.second)) {
                    latest[merchant] = rate to occurredAt
                }
            }
            return HistoricalExchangeRateIndex(latest.mapValues { it.value.first })
        }

        private fun isNewer(candidate: Instant?, current: Instant?): Boolean {
            if (candidate == null) return current == null
            if (current == null) return true
            return candidate.isAfter(current)
        }

        private fun String.normalizeMerchantKey(): String =
            lowercase(Locale.ROOT).trim()
    }
}
