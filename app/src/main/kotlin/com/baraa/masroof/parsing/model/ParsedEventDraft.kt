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
 * Intermediate parse draft before validation finalizes a [ParsedEvent].
 *
 * Must never carry ownership or [com.baraa.masroof.domain.model.FinancialTransactionType].
 *
 * [details] holds typed parse-time facts not modeled on [ParsedEvent].
 * [amountCandidates] / [selectedAmount] preserve extraction provenance for V-001…V-007.
 *
 * [occurredAt] ([Instant]) is intentionally unused when only a local SMS timestamp
 * is known; use [ParsedEventDetails.occurredAtLocal] instead.
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
    val details: ParsedEventDetails = ParsedEventDetails(),
    val amountCandidates: List<AmountCandidate> = emptyList(),
    val selectedAmount: AmountCandidate? = null,
) {
    /**
     * Builds a [ParsedEvent] only when required identity fields are present and a
     * declared [ParseStatus.SUCCESS] draft is complete enough for automatic use
     * (financial families must carry an amount).
     *
     * Callers that emit [ParseResult.Success] must go through
     * [com.baraa.masroof.parsing.finalize.ParseFinalizer] so validation runs first.
     */
    fun toParsedEvent(id: String): ParsedEvent {
        val family = requireNotNull(messageFamily) { "messageFamily required to build ParsedEvent" }
        val resolvedBank = requireNotNull(bank) { "bank required to build ParsedEvent" }
        val status = requireNotNull(parseStatus) { "parseStatus required to build ParsedEvent" }
        val conf = requireNotNull(confidence) { "confidence required to build ParsedEvent" }

        if (status == ParseStatus.SUCCESS && family.isFinancialFamily && amount == null) {
            throw IllegalArgumentException(
                "Cannot build SUCCESS ParsedEvent for financial family $family without amount",
            )
        }

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

    private val MessageFamily.isFinancialFamily: Boolean
        get() = when (this) {
            MessageFamily.PURCHASE,
            MessageFamily.TRANSFER_IN,
            MessageFamily.TRANSFER_OUT,
            MessageFamily.CARD_PAYMENT,
            MessageFamily.BILL_PAYMENT,
            MessageFamily.FINANCING_INSTALLMENT,
            MessageFamily.WITHDRAWAL,
            MessageFamily.REFUND,
            MessageFamily.FEE,
            -> true

            MessageFamily.BALANCE_NOTICE,
            MessageFamily.OTP,
            MessageFamily.NON_FINANCIAL,
            MessageFamily.UNKNOWN,
            -> false
        }
}
