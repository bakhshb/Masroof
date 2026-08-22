package com.baraa.masroof.presentation.common

import com.baraa.masroof.presentation.dashboard.SmsRescanStatus
import com.baraa.masroof.presentation.settings.SmsImportMessage
import com.baraa.masroof.sms.scanner.SmsScanUserOutcome

fun SmsScanUserOutcome.toRescanStatus(): SmsRescanStatus =
    when (this) {
        SmsScanUserOutcome.PERMISSION_DENIED -> SmsRescanStatus.PERMISSION_DENIED
        SmsScanUserOutcome.FAILED -> SmsRescanStatus.FAILED
        SmsScanUserOutcome.NO_MESSAGES -> SmsRescanStatus.NO_MESSAGES
        SmsScanUserOutcome.NO_BANK_SMS -> SmsRescanStatus.NO_BANK_SMS
        SmsScanUserOutcome.OK -> SmsRescanStatus.OK
        SmsScanUserOutcome.ALREADY_UP_TO_DATE -> SmsRescanStatus.ALREADY_UP_TO_DATE
        SmsScanUserOutcome.NEEDS_REVIEW -> SmsRescanStatus.NEEDS_REVIEW
        SmsScanUserOutcome.NO_NEW_TRANSACTIONS -> SmsRescanStatus.NO_TRANSACTIONS
    }

fun SmsScanUserOutcome.toImportMessage(): SmsImportMessage =
    when (this) {
        SmsScanUserOutcome.PERMISSION_DENIED -> SmsImportMessage.PERMISSION_DENIED
        SmsScanUserOutcome.FAILED -> SmsImportMessage.FAILED
        SmsScanUserOutcome.NO_MESSAGES -> SmsImportMessage.NO_MESSAGES
        SmsScanUserOutcome.NO_BANK_SMS -> SmsImportMessage.NO_BANK_SMS
        SmsScanUserOutcome.OK -> SmsImportMessage.OK
        SmsScanUserOutcome.ALREADY_UP_TO_DATE -> SmsImportMessage.ALREADY_UP_TO_DATE
        SmsScanUserOutcome.NEEDS_REVIEW -> SmsImportMessage.NEEDS_REVIEW
        SmsScanUserOutcome.NO_NEW_TRANSACTIONS -> SmsImportMessage.NO_TRANSACTIONS
    }
