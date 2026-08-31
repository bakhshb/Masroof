package com.baraa.masroof.application.locale

import android.content.Context

/**
 * Reads the persisted locale tag before the composition root is available.
 */
object AppLocaleBootstrap {
    fun readStoredLanguageTag(context: Context): String {
        val prefs = context.getSharedPreferences(AppLocalePreferences.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(AppLocalePreferences.KEY_LANGUAGE_TAG, AppLocale.DEFAULT_TAG)
            ?: AppLocale.DEFAULT_TAG
    }
}
