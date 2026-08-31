package com.baraa.masroof.application.update

internal object UpdateManifestSelector {
    fun highestVersionCode(manifests: Iterable<UpdateManifest>): UpdateManifest? =
        manifests.maxByOrNull { it.versionCode }
}
