package com.baraa.masroof.presentation.locale

import android.content.Context
import android.content.res.Configuration
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.data.preferences.SharedPrefsAppLocaleRepository
import java.util.Locale

object AppLocaleContext {
    fun readStoredLanguageTag(context: Context): String {
        val prefs = context.getSharedPreferences(
            SharedPrefsAppLocaleRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        return prefs.getString(
            SharedPrefsAppLocaleRepository.KEY_LANGUAGE_TAG,
            AppLocale.DEFAULT_TAG,
        ) ?: AppLocale.DEFAULT_TAG
    }

    fun wrap(context: Context, languageTag: String): Context {
        val locale = AppLocale.displayLocale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
