package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.CreditCardMessageHeuristics
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Recognizes owned Mada/debit registry cards for dashboard hierarchy and spending.
 *
 * Explicit [CardType.DEBIT] is authoritative. Untyped cards with Mada network, a linked
 * account, or Mada SMS history are treated as debit so they appear under accounts summary
 * instead of being dropped or misclassified as credit.
 */
object CardRegistryDebitClassifier {
    fun isDebitRegistryEntry(
        entry: CardRegistryEntry,
        parsedRecords: List<ParsedEventRecord> = emptyList(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
    ): Boolean {
        when (entry.cardType) {
            CardType.DEBIT -> return true
            CardType.CREDIT -> return false
            null -> Unit
        }
        if (entry.cardNetwork == CardNetwork.MADA) return true
        if (entry.linkedAccount != null) return true
        return inferDebitFromSms(entry, parsedRecords, rawSmsById)
    }

    fun isCreditRegistryEntry(
        entry: CardRegistryEntry,
        parsedRecords: List<ParsedEventRecord> = emptyList(),
        rawSmsById: Map<String, RawSms> = emptyMap(),
    ): Boolean =
        entry.cardType == CardType.CREDIT ||
            (entry.cardType == null && !isDebitRegistryEntry(entry, parsedRecords, rawSmsById))

    private fun inferDebitFromSms(
        entry: CardRegistryEntry,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        for (record in parsedRecords) {
            val event = record.event
            if (event.bank != entry.bank) continue
            if (event.cardRef?.last4 != entry.last4) continue
            val body = rawSmsById[event.rawSmsId]?.body ?: continue
            if (CreditCardMessageHeuristics.isDebitCardSms(body)) return true
        }
        return false
    }
}
