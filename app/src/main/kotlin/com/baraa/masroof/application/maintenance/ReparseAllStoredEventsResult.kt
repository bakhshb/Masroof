package com.baraa.masroof.application.maintenance

/**
 * Outcome of a full stored-SMS reparse pass.
 */
data class ReparseAllStoredEventsResult(
    val refreshedCount: Int,
    val failedCount: Int,
) {
    val succeeded: Boolean get() = failedCount == 0
}
