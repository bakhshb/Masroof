package com.baraa.masroof.sms

/**
 * One SMS row read from the device inbox.
 *
 * @param id        stable row id from the SMS provider (or 0 if unknown)
 * @param sender    sender address (phone number) or null when unavailable
 * @param body      message body, may be null/empty if the provider hid it
 * @param timestamp epoch millis the message was received
 */
data class SmsMessage(
    val id: Long,
    val sender: String?,
    val body: String?,
    val timestamp: Long,
)
