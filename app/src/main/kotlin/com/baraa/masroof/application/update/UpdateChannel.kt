package com.baraa.masroof.application.update

enum class UpdateChannel {
    STABLE,
    NIGHTLY,
    ;

    fun storageValue(): String = name.lowercase()

    fun acceptsManifestChannel(manifestChannel: String): Boolean =
        when (this) {
            STABLE -> manifestChannel == STABLE.storageValue()
            NIGHTLY ->
                manifestChannel == STABLE.storageValue() ||
                    manifestChannel == NIGHTLY.storageValue()
        }

    companion object {
        val DEFAULT: UpdateChannel = STABLE

        fun fromStorage(value: String?): UpdateChannel =
            when (value?.trim()?.lowercase()) {
                NIGHTLY.storageValue() -> NIGHTLY
                else -> STABLE
            }

        fun normalizeManifestChannel(value: String?): String =
            when (value?.trim()?.lowercase()) {
                NIGHTLY.storageValue() -> NIGHTLY.storageValue()
                else -> STABLE.storageValue()
            }
    }
}
