package com.baraa.masroof.application.onboarding

import com.baraa.masroof.application.AppContainer
import java.time.Instant
import java.time.ZoneId

/**
 * Re-runs historical SMS import using the saved onboarding import boundary.
 */
class HistoricalSmsRescanService(
    private val container: AppContainer,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun rescan(): HistoricalImportResult {
        val epoch = container.onboardingPreferencesRepository.getHistoricalImportStartEpochMillis()
        val receivedAfter = epoch?.let(Instant::ofEpochMilli)
            ?: ImportDatePolicy.toStartOfDayInstant(
                ImportDatePolicy.last27th(
                    java.time.LocalDate.now(zoneId),
                ),
                zoneId,
            )

        val result = container.historicalSmsScanner.scan(receivedAfter)
        if (result.failure == null) {
            container.reparseAllStoredEvents()
        }
        return result.toHistoricalImportResult()
    }
}
