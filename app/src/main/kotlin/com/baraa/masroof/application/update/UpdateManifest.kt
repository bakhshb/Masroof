package com.baraa.masroof.application.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkFileName: String,
    val sha256: String,
    val releaseNotes: String? = null,
    val channel: String = UpdateChannel.STABLE.storageValue(),
    val releaseTag: String? = null,
) {
    val normalizedChannel: String
        get() = UpdateChannel.normalizeManifestChannel(channel)

    fun withReleaseTag(tag: String): UpdateManifest =
        if (releaseTag == tag) {
            this
        } else {
            copy(releaseTag = tag)
        }

    fun resolvedReleaseTag(): String {
        releaseTag?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val trimmedName = versionName.trim()
        return if (trimmedName.startsWith("v")) trimmedName else "v$trimmedName"
    }
}
