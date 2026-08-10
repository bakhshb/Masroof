package com.baraa.masroof.domain.model

import com.baraa.masroof.core.money.Money
import java.time.Instant

/**
 * Structured interpretation of a single SMS.
 *
 * This is **not** a final [FinancialTransaction]. Ownership resolution, related
 * event matching, and financial assembly happen later.
 *
 * [purchaseChannel] is set when [messageFamily] is [MessageFamily.PURCHASE].
 * [bankNetworkType] describes bank routing only — never self-transfer ownership.
 */
data class ParsedEvent(
    val id: String,
    val rawSmsId: String,
    val bank: Bank,
    val messageFamily: MessageFamily,
    val direction: MoneyDirection?,
    val amount: Money?,
    val purchaseChannel: PurchaseChannel?,
    val sourceAccountRef: AccountReference?,
    val destinationAccountRef: AccountReference?,
    val cardRef: CardReference?,
    val merchant: String?,
    val counterparty: String?,
    val occurredAt: Instant?,
    val bankNetworkType: BankNetworkType?,
    val confidence: Confidence,
    val parseStatus: ParseStatus,
)
