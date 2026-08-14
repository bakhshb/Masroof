package com.baraa.masroof.application.update

class UpdateChecker(
    private val installedVersionCode: Int,
) {
    fun isUpdateAvailable(manifest: UpdateManifest): Boolean =
        manifest.versionCode > installedVersionCode

    fun evaluate(manifest: UpdateManifest): UpdateAvailability =
        if (isUpdateAvailable(manifest)) {
            UpdateAvailability.Available(manifest)
        } else {
            UpdateAvailability.UpToDate
        }
}

sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability

    data class Available(val manifest: UpdateManifest) : UpdateAvailability
}
