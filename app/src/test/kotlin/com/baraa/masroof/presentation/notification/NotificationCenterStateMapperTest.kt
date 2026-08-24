package com.baraa.masroof.presentation.notification

import com.baraa.masroof.application.update.UpdateManifest
import com.baraa.masroof.presentation.dashboard.DashboardUiState
import com.baraa.masroof.presentation.settings.AppUpdateUiState
import com.baraa.masroof.presentation.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationCenterStateMapperTest {
    private val updateManifest =
        UpdateManifest(
            versionCode = 99,
            versionName = "9.9.0",
            apkFileName = "masroof.apk",
            sha256 = "abc",
            releaseNotes = null,
        )

    @Test
    fun mapsAvailableAndReadyUpdateVersions() {
        val availableState = notificationCenterExternalState(
            dashboardState = DashboardUiState(periodLabel = "Aug 2026"),
            settingsState = SettingsUiState(
                updateState = AppUpdateUiState.Available(updateManifest),
            ),
        )
        assertEquals("9.9.0", availableState.updateAvailableVersion)
        assertNull(availableState.updateReadyVersion)

        val readyState = notificationCenterExternalState(
            dashboardState = DashboardUiState(periodLabel = "Aug 2026"),
            settingsState = SettingsUiState(
                updateState = AppUpdateUiState.ReadyToInstall(updateManifest),
            ),
        )
        assertNull(readyState.updateAvailableVersion)
        assertEquals("9.9.0", readyState.updateReadyVersion)
    }

    @Test
    fun idleUpdateStateOmitsUpdateNotifications() {
        val state = notificationCenterExternalState(
            dashboardState = DashboardUiState(periodLabel = "Aug 2026"),
            settingsState = SettingsUiState(updateState = AppUpdateUiState.Idle),
        )

        assertNull(state.updateAvailableVersion)
        assertNull(state.updateReadyVersion)
    }
}
