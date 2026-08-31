package com.baraa.masroof.application.onboarding

import java.time.Instant

fun interface HistoricalImportGateway {
    suspend fun scan(receivedAfter: Instant?): HistoricalImportResult
}
