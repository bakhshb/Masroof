package com.baraa.masroof.domain.model

/**
 * Channel through which a [MessageFamily.PURCHASE] occurred.
 *
 * Not an ownership concept and not a substitute for [MessageFamily].
 */
enum class PurchaseChannel {
    POS,
    ONLINE,
    UNKNOWN,
}
