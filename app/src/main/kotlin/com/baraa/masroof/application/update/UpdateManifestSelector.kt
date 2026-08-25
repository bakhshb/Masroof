package com.baraa.masroof.application.update

object UpdateManifestSelector {
    fun bestForChannel(
        channel: UpdateChannel,
        installedVersionCode: Int,
        manifests: List<UpdateManifest>,
    ): UpdateManifest? =
        manifests
            .filter { channel.acceptsManifestChannel(it.normalizedChannel) }
            .filter { it.versionCode > installedVersionCode }
            .maxByOrNull { it.versionCode }
}
