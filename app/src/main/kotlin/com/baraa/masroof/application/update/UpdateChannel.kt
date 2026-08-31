package com.baraa.masroof.application.update

enum class UpdateChannel(val storageValue: String) {
    STABLE("stable"),
    NIGHTLY("nightly"),
    ;

    companion object {
        fun fromStorageValue(value: String?): UpdateChannel =
            entries.firstOrNull { it.storageValue == value } ?: STABLE
    }
}
