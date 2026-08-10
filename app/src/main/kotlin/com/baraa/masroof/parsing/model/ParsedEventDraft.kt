package com.baraa.masroof.parsing.model

import com.baraa.masroof.core.money.Money
import com.baraa.masroof.domain.model.AccountReference
import com.baraa.masroof.domain.model.Bank
import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.CardReference
import com.baraa.masroof.domain.model.Confidence
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.MoneyDirection
import com.baraa.masroof.domain.model.ParseStatus
import com.baraa.masroof.domain.model.ParsedEvent
import com.baraa.masroof.domain.model.PurchaseChannel
import java.time.Instant

/**
 * Mutable-construction-friendly intermediate parse draft.
 *
 * Used before validation finalizes a [ParsedEvent]. Fields may be incomplete.
 * Must never carry ownership or [com.baraa.masroof.domain.model.FinancialTransactionType].
 */
data class ParsedEventDraft(
    val rawSmsId: String,
    val bank: Bank? = null,
    val messageFamily: MessageFamily? = null,
    val direction: MoneyDirection? = null,
    val amount: Money? = null,
    val purchaseChannel: PurchaseChannel? = null,
    val sourceAccountRef: AccountReference? = null,
    val destinationAccountRef: AccountReference? = null,
    val cardRef: CardReference? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val occurredAt: Instant? = null,
    val bankNetworkType: BankNetworkType? = null,
    val confidence: Confidence? = null,
    val parseStatus: ParseStatus? = null,
) {
    fun toParsedEvent(id: String): ParsedEvent {
        val family = requireNotNull(messageFamily) { "messageFamily required to build ParsedEvent" }
        val resolvedBank = requireNotNull(bank) { "bank required to build ParsedEvent" }
        val status = requireNotNull(parseStatus) { "parseStatus required to build ParsedEvent" }
        val conf = requireNotNull(confidence) { "confidence required to build ParsedEvent" }
        return ParsedEvent(
            id = id,
            rawSmsId = rawSmsId,
            bank = resolvedBank,
            messageFamily = family,
            direction = direction,
            amount = amount,
            purchaseChannel = purchaseChannel,
            sourceAccountRef = sourceAccountRef,
            destinationAccountRef = destinationAccountRef,
            cardRef = cardRef,
            merchant = merchant,
            counterparty = counterparty,
            occurredAt = occurredAt,
            bankNetworkType = bankNetworkType,
            confidence = conf,
            parseStatus = status,
        )
    }
}
