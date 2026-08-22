package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.dashboard.SmsRescanStatus
import com.baraa.masroof.presentation.settings.SmsImportMessage
import com.baraa.masroof.sms.scanner.SmsScanUserOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsScanOutcomePresentationTest {
    @Test
    fun everyOutcome_mapsToRescanStatus() {
        SmsScanUserOutcome.entries.forEach { outcome ->
            val mapped = outcome.toRescanStatus()
            when (outcome) {
                SmsScanUserOutcome.OK -> assertEquals(SmsRescanStatus.OK, mapped)
                SmsScanUserOutcome.ALREADY_UP_TO_DATE -> assertEquals(SmsRescanStatus.ALREADY_UP_TO_DATE, mapped)
                SmsScanUserOutcome.NEEDS_REVIEW -> assertEquals(SmsRescanStatus.NEEDS_REVIEW, mapped)
                SmsScanUserOutcome.NO_MESSAGES -> assertEquals(SmsRescanStatus.NO_MESSAGES, mapped)
                SmsScanUserOutcome.NO_BANK_SMS -> assertEquals(SmsRescanStatus.NO_BANK_SMS, mapped)
                SmsScanUserOutcome.NO_NEW_TRANSACTIONS -> assertEquals(SmsRescanStatus.NO_TRANSACTIONS, mapped)
                SmsScanUserOutcome.PERMISSION_DENIED -> assertEquals(SmsRescanStatus.PERMISSION_DENIED, mapped)
                SmsScanUserOutcome.FAILED -> assertEquals(SmsRescanStatus.FAILED, mapped)
            }
        }
    }

    @Test
    fun everyOutcome_mapsToImportMessage() {
        SmsScanUserOutcome.entries.forEach { outcome ->
            val mapped = outcome.toImportMessage()
            when (outcome) {
                SmsScanUserOutcome.OK -> assertEquals(SmsImportMessage.OK, mapped)
                SmsScanUserOutcome.ALREADY_UP_TO_DATE -> assertEquals(SmsImportMessage.ALREADY_UP_TO_DATE, mapped)
                SmsScanUserOutcome.NEEDS_REVIEW -> assertEquals(SmsImportMessage.NEEDS_REVIEW, mapped)
                SmsScanUserOutcome.NO_MESSAGES -> assertEquals(SmsImportMessage.NO_MESSAGES, mapped)
                SmsScanUserOutcome.NO_BANK_SMS -> assertEquals(SmsImportMessage.NO_BANK_SMS, mapped)
                SmsScanUserOutcome.NO_NEW_TRANSACTIONS -> assertEquals(SmsImportMessage.NO_TRANSACTIONS, mapped)
                SmsScanUserOutcome.PERMISSION_DENIED -> assertEquals(SmsImportMessage.PERMISSION_DENIED, mapped)
                SmsScanUserOutcome.FAILED -> assertEquals(SmsImportMessage.FAILED, mapped)
            }
        }
    }
}
