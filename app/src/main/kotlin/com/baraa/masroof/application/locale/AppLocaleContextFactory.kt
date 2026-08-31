package com.baraa.masroof.application.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Wraps a [Context] with the user's selected display locale.
 */
object AppLocaleContextFactory {
    fun wrap(context: Context, languageTag: String): Context {
        val locale = AppLocale.displayLocale(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
