package com.baraa.masroof.application.locale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLocaleBootstrapTest {
    @Test
    fun readStoredLanguageTag_defaultsToArabicWhenUnset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(AppLocale.DEFAULT_TAG, AppLocaleBootstrap.readStoredLanguageTag(context))
    }

    @Test
    fun readStoredLanguageTag_returnsPersistedTag() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(AppLocalePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AppLocalePreferences.KEY_LANGUAGE_TAG, AppLocale.TAG_EN)
            .commit()
        assertEquals(AppLocale.TAG_EN, AppLocaleBootstrap.readStoredLanguageTag(context))
    }
}
