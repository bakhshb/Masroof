package com.baraa.masroof.sms

import com.baraa.masroof.transaction.ParsedTransaction

/**
 * One SMS row read from the device inbox, annotated with both the bank-filter
 * result and a structured [ParsedTransaction] (if parsing produced one).
 *
 * @param id          stable row id from the SMS provider (or 0 if unknown)
 * @param sender      sender address (phone number) or null when unavailable
 * @param body        message body, may be null/empty if the provider hid it
 * @param timestamp   epoch millis the message was received (from SMS metadata)
 * @param matchReason reason this message matched (or did not match) the bank
 *                    filter; see [BankSmsFilter.classifyMessage]
 * @param parsed      structured transaction extracted from the body, or null
 *                    if the body was null / empty / the parser deferred
 */
data class SmsMessage(
    val id: Long,
    val sender: String?,
    val body: String?,
    val timestamp: Long,
    val matchReason: MatchReason = MatchReason.NONE,
    val parsed: ParsedTransaction? = null,
)
