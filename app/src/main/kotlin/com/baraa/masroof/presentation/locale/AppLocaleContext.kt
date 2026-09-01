package com.baraa.masroof.presentation.locale

import android.content.Context
import com.baraa.masroof.application.locale.AppLocaleBootstrap
import com.baraa.masroof.application.locale.AppLocaleContextFactory

object AppLocaleContext {
    fun readStoredLanguageTag(context: Context): String =
        AppLocaleBootstrap.readStoredLanguageTag(context)

    fun wrap(context: Context, languageTag: String): Context =
        AppLocaleContextFactory.wrap(context, languageTag)
}
