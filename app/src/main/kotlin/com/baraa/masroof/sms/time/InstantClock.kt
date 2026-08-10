package com.baraa.masroof.sms.time

import java.time.Instant

/**
 * Injectable clock for device receipt timestamps (not SMSC message time).
 */
fun interface InstantClock {
    fun now(): Instant

    companion object {
        val System: InstantClock = InstantClock { Instant.now() }
    }
}
