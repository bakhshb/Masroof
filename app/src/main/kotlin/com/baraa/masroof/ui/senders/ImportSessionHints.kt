package com.baraa.masroof.ui.senders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One-shot hints for the Import screen (e.g. after SMS bind success).
 * Consumed on first read so later visits keep the user's last picker choice.
 */
object ImportSessionHints {
    @Volatile
    var preferredFromDate: LocalDate? = null
        private set

    @Volatile
    var preferredSender: String? = null
        private set

    fun setPreferredFromDate(date: LocalDate?) {
        preferredFromDate = date
    }

    fun setPreferredFromEpochMillis(millis: Long?) {
        if (millis == null || millis <= 0L) return
        preferredFromDate = Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    /** Returns and clears the preferred from-date. */
    fun consumePreferredFromDate(): LocalDate? {
        val value = preferredFromDate
        preferredFromDate = null
        return value
    }

    fun setPreferredSender(sender: String?) {
        preferredSender = sender?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun peekPreferredSender(): String? = preferredSender

    /** Returns and clears the preferred sender (for prefill on account bind). */
    fun consumePreferredSender(): String? {
        val value = preferredSender
        preferredSender = null
        return value
    }
}
