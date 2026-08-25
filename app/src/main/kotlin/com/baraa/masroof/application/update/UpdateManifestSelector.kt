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
            .maxWithOrNull(::compareCandidates)

    fun pickBetter(current: UpdateManifest?, candidate: UpdateManifest): UpdateManifest =
        when {
            current == null -> candidate
            compareCandidates(current, candidate) < 0 -> candidate
            else -> current
        }

    private fun compareCandidates(left: UpdateManifest, right: UpdateManifest): Int {
        val codeCompare = left.versionCode.compareTo(right.versionCode)
        if (codeCompare != 0) {
            return codeCompare
        }
        return stableRank(left).compareTo(stableRank(right))
    }

    private fun stableRank(manifest: UpdateManifest): Int =
        if (manifest.normalizedChannel == UpdateChannel.STABLE.storageValue()) 1 else 0
}
