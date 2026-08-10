package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.FinancialContainer
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.PurchaseChannel

/**
 * Immutable inputs for financial classification.
 *
 * Ownership is supplied via resolved [source], [destination], and/or [instrument]
 * containers — not inferred from [bankNetworkType] or SMS wording.
 *
 * [bankNetworkType] is accepted only so callers can prove network independence;
 * it must not drive ownership or self-transfer decisions.
 */
data class ClassificationContext(
    val messageFamily: MessageFamily,
    val source: FinancialContainer? = null,
    val destination: FinancialContainer? = null,
    /**
     * Account or card that funded/received a purchase, withdrawal, fee, or refund
     * when a single-sided instrument is known.
     */
    val instrument: FinancialContainer? = null,
    val purchaseChannel: PurchaseChannel? = null,
    val bankNetworkType: BankNetworkType? = null,
)
