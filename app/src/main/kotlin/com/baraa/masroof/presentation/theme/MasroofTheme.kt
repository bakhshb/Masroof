package com.baraa.masroof.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.baraa.masroof.application.locale.AppLocale

@Composable
fun MasroofTheme(content: @Composable () -> Unit) {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val layoutDirection = if (AppLocale.isRtl(languageTag)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(content = content)
    }
}
