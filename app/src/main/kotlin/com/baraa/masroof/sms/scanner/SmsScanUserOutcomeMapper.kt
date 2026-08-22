package com.baraa.masroof.sms.scanner

enum class SmsScanUserOutcome {
    OK,
    ALREADY_UP_TO_DATE,
    NEEDS_REVIEW,
    NO_MESSAGES,
    NO_BANK_SMS,
    NO_NEW_TRANSACTIONS,
    PERMISSION_DENIED,
    FAILED,
}

/**
 * Maps operational [SmsScanResult] counters to user-facing outcomes.
 *
 * [SmsScanResult.parsed] counts only newly ingested rows that parsed cleanly — not
 * duplicates and not rows routed to the review queue — so `parsed == 0` alone is not an error.
 */
object SmsScanUserOutcomeMapper {
    fun map(result: SmsScanResult): SmsScanUserOutcome {
        when (result.failure) {
            SmsScanFailure.PermissionDenied -> return SmsScanUserOutcome.PERMISSION_DENIED
            is SmsScanFailure.ProviderError -> return SmsScanUserOutcome.FAILED
            null -> Unit
        }
        return when {
            result.scanned == 0 -> SmsScanUserOutcome.NO_MESSAGES
            result.notRelevant == result.scanned -> SmsScanUserOutcome.NO_BANK_SMS
            result.parsed > 0 -> SmsScanUserOutcome.OK
            result.reviewRequired > 0 -> SmsScanUserOutcome.NEEDS_REVIEW
            result.failed > 0 -> SmsScanUserOutcome.FAILED
            isAlreadyImported(result) -> SmsScanUserOutcome.ALREADY_UP_TO_DATE
            else -> SmsScanUserOutcome.NO_NEW_TRANSACTIONS
        }
    }

    private fun isAlreadyImported(result: SmsScanResult): Boolean {
        val bankFacing = result.scanned - result.notRelevant - result.skippedMalformed
        return bankFacing > 0 && result.duplicates >= bankFacing
    }
}
