package com.baraa.masroof.parsing.model

import java.time.Instant

/**
 * Raw SMS facts presented to the parsing pipeline.
 *
 * Does not carry ownership, financial treatment, or transaction conclusions.
 */
data class SmsParseInput(
    val rawSmsId: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
)
