package com.baraa.masroof.application.onboarding

import com.baraa.masroof.sms.scanner.SmsScanResult
import java.time.Instant

fun interface HistoricalImportGateway {
    suspend fun scan(receivedAfter: Instant?): SmsScanResult
}
