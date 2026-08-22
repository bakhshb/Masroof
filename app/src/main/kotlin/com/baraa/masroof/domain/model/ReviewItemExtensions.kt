package com.baraa.masroof.domain.model

/**
 * User-marked non-financial / ignored review rows (dismissed from review queue or transaction detail).
 *
 * Intentionally excludes other resolved kinds such as [ReviewResolutionKind.USER_CORRECTION].
 */
fun ReviewItem.isUserIgnored(): Boolean =
    status == ReviewStatus.RESOLVED &&
        resolutionKind == ReviewResolutionKind.USER_NON_FINANCIAL
