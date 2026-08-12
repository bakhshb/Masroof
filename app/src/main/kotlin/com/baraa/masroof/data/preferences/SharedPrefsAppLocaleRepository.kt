package com.baraa.masroof.data.preferences

import android.content.SharedPreferences
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.locale.AppLocaleRepository

class SharedPrefsAppLocaleRepository(
    private val prefs: SharedPreferences,
) : AppLocaleRepository {
    override fun getLanguageTag(): String =
        prefs.getString(KEY_LANGUAGE_TAG, AppLocale.DEFAULT_TAG) ?: AppLocale.DEFAULT_TAG

    override fun setLanguageTag(languageTag: String) {
        val normalized = when (languageTag) {
            AppLocale.TAG_EN -> AppLocale.TAG_EN
            else -> AppLocale.TAG_AR
        }
        prefs.edit().putString(KEY_LANGUAGE_TAG, normalized).apply()
    }

    companion object {
        const val PREFS_NAME: String = "app_locale_prefs"
        const val KEY_LANGUAGE_TAG: String = "language_tag"
    }
}
