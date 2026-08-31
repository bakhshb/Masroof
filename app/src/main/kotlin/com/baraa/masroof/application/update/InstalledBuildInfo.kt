package com.baraa.masroof.application.update

object InstalledBuildInfo {
    fun isNightlyBuild(versionName: String): Boolean =
        versionName.contains("-nightly.", ignoreCase = true)
}
