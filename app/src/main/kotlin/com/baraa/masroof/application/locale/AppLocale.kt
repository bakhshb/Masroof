package com.baraa.masroof.application.locale

import java.util.Locale

object AppLocale {
    const val TAG_AR: String = "ar"
    const val TAG_EN: String = "en"
    const val DEFAULT_TAG: String = TAG_AR

    fun displayLocale(languageTag: String): Locale =
        when (languageTag) {
            TAG_EN -> Locale.ENGLISH
            else -> Locale.forLanguageTag(TAG_AR)
        }

    fun isRtl(languageTag: String): Boolean = languageTag != TAG_EN
}
