package com.baraa.masroof.application.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkFileName: String,
    val sha256: String,
    val releaseNotes: String? = null,
)
