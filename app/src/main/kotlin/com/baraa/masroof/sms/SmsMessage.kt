package com.baraa.masroof.sms

/**
 * One SMS row read from the device inbox, annotated with the bank-filter result.
 *
 * @param id          stable row id from the SMS provider (or 0 if unknown)
 * @param sender      sender address (phone number) or null when unavailable
 * @param body        message body, may be null/empty if the provider hid it
 * @param timestamp   epoch millis the message was received
 * @param matchReason reason this message matched (or did not match) the bank
 *                    filter; see [BankSmsFilter.classifyMessage]
 */
data class SmsMessage(
    val id: Long,
    val sender: String?,
    val body: String?,
    val timestamp: Long,
    val matchReason: MatchReason = MatchReason.NONE,
)
