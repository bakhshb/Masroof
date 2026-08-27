package com.baraa.masroof.application.dashboard

import com.baraa.masroof.bank.aljazira.CreditCardMessageHeuristics
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.CardNetwork
import com.baraa.masroof.domain.model.CardRegistryEntry
import com.baraa.masroof.domain.model.CardType
import com.baraa.masroof.domain.model.RawSms
import com.baraa.masroof.parsing.repository.ParsedEventRecord

/**
 * Resolves whether a registry card is a debit (Mada) card for dashboard purposes.
 *
 * Settings lists all owned cards; the dashboard only treats cards as Mada when they are
 * explicitly marked debit, carry the Mada network, or have debit SMS evidence.
 */
object DebitCardRegistryInferrer {
    fun isDebitCard(
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
        return hasDebitSmsEvidence(
            bank = entry.bank,
            cardLast4 = entry.last4,
            parsedRecords = parsedRecords,
            rawSmsById = rawSmsById,
        )
    }

    private fun hasDebitSmsEvidence(
        bank: Bank,
        cardLast4: String,
        parsedRecords: List<ParsedEventRecord>,
        rawSmsById: Map<String, RawSms>,
    ): Boolean {
        for (record in parsedRecords) {
            val event = record.event
            if (event.bank != bank) continue
            if (event.cardRef?.last4 != cardLast4) continue
            val body = rawSmsById[event.rawSmsId]?.body ?: continue
            if (CreditCardMessageHeuristics.isCreditCardSms(body)) continue
            if (CreditCardMessageHeuristics.isDebitCardSms(body)) return true
        }
        return false
    }
}
