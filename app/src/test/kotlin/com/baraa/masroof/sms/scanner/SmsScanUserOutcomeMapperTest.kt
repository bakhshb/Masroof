package com.baraa.masroof.sms.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsScanUserOutcomeMapperTest {
    @Test
    fun allDuplicates_isAlreadyUpToDate() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 5,
                duplicates = 5,
                parsed = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.ALREADY_UP_TO_DATE, outcome)
    }

    @Test
    fun newReviewRequired_isNeedsReview() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 3,
                duplicates = 1,
                reviewRequired = 2,
                parsed = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.NEEDS_REVIEW, outcome)
    }

    @Test
    fun newlyParsed_isOk() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 2,
                parsed = 2,
                duplicates = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.OK, outcome)
    }

    @Test
    fun bankMessagesWithoutParseOrDuplicate_isNoNewTransactions() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 2,
                nonFinancial = 2,
                parsed = 0,
                duplicates = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.NO_NEW_TRANSACTIONS, outcome)
    }

    @Test
    fun bankMessagesWithFailures_isFailed() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 3,
                duplicates = 1,
                failed = 2,
                parsed = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.FAILED, outcome)
    }

    @Test
    fun unsupportedBankMessages_isNoNewTransactions() {
        val outcome = SmsScanUserOutcomeMapper.map(
            SmsScanResult(
                scanned = 2,
                unsupported = 2,
                parsed = 0,
                duplicates = 0,
            ),
        )
        assertEquals(SmsScanUserOutcome.NO_NEW_TRANSACTIONS, outcome)
    }
}
