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

    fun cardBankId(containerId: String?): String? {
        if (containerId.isNullOrBlank() || !containerId.startsWith("card:")) return null
        val bankId = containerId.removePrefix("card:").substringBefore(':').trim()
        return bankId.takeIf { it.isNotEmpty() }
    }

    fun accountMaskedNumber(containerId: String?): String? {
        if (containerId.isNullOrBlank()) return null
        if (!containerId.startsWith("account:")) return null
        return containerId.substringAfterLast(':').trim().takeIf { it.isNotEmpty() }
    }

    fun accountContainerIdsFromContainers(
        sourceContainerId: String?,
        destinationContainerId: String?,
    ): Set<String> = buildSet {
        if (!sourceContainerId.isNullOrBlank() && sourceContainerId.startsWith("account:")) {
            add(sourceContainerId)
        }
        if (!destinationContainerId.isNullOrBlank() && destinationContainerId.startsWith("account:")) {
            add(destinationContainerId)
        }
    }
}
