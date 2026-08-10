package com.baraa.masroof.sms.model

import java.time.Instant

/**
 * Provider-agnostic SMS row used by historical scanning.
 *
 * [providerMessageId] is the Android SMS `_ID` when known; null for live paths
 * that have not been assigned a provider row yet.
 */
data class ProviderSmsRecord(
    val providerMessageId: String?,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
