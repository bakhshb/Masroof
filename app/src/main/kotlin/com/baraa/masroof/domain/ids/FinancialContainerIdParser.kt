package com.baraa.masroof.domain.ids

/**
 * Parses opaque [FinancialContainerIdFactory] ids for UI filtering.
 */
object FinancialContainerIdParser {
    fun cardLast4(containerId: String?): String? {
        if (containerId.isNullOrBlank()) return null
        if (!containerId.startsWith("card:")) return null
        val last4 = containerId.substringAfterLast(':').trim()
        return last4.takeIf { it.isNotEmpty() }
    }

    fun cardLast4FromContainers(sourceContainerId: String?, destinationContainerId: String?): String? =
        cardLast4(sourceContainerId) ?: cardLast4(destinationContainerId)
}
