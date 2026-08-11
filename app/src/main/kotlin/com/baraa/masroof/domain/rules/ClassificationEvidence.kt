package com.baraa.masroof.domain.rules

import com.baraa.masroof.domain.model.BankNetworkType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.PurchaseChannel

/**
 * Classification inputs using only facts the caller genuinely knows.
 *
 * Prefer this over constructing temporary [com.baraa.masroof.domain.model.Account]
 * / [com.baraa.masroof.domain.model.Card] objects solely to satisfy the classifier.
 */
data class ClassificationEvidence(
    val messageFamily: MessageFamily,
    val source: ResolvedContainerFacts? = null,
    val destination: ResolvedContainerFacts? = null,
    val instrument: ResolvedContainerFacts? = null,
    val purchaseChannel: PurchaseChannel? = null,
    val bankNetworkType: BankNetworkType? = null,
)
