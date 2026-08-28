package com.baraa.masroof.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.baraa.masroof.R
import com.baraa.masroof.application.locale.AppLocale
import com.baraa.masroof.application.theme.ThemeMode
import com.baraa.masroof.presentation.common.MasroofIcons
import com.baraa.masroof.presentation.common.MasroofSecondaryScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppScreen(
    languageTag: String,
    themeMode: ThemeMode,
    onBack: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    if (showLanguageDialog) {
        SettingsLanguageDialog(
            selectedLanguageTag = languageTag,
            onDismiss = { showLanguageDialog = false },
            onSelectLanguage = { tag ->
                showLanguageDialog = false
                onSelectLanguage(tag)
            },
        )
    }
    if (showThemeDialog) {
        SettingsThemeDialog(
            selectedMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelectMode = { mode ->
                showThemeDialog = false
                onSelectTheme(mode)
            },
        )
    }

    MasroofSecondaryScaffold(
        title = stringResource(R.string.settings_app_section),
        onBack = onBack,
        backContentDescription = stringResource(R.string.settings_back),
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionGap),
        ) {
            SettingsNavRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language_title),
                subtitle = languageSubtitle(languageTag),
                onClick = { showLanguageDialog = true },
            )
            SettingsNavRow(
                icon = MasroofIcons.theme,
                title = stringResource(R.string.settings_theme_title),
                subtitle = themeSubtitle(themeMode),
                onClick = { showThemeDialog = true },
            )
        }
    }
}

@Composable
private fun languageSubtitle(languageTag: String): String =
    if (AppLocale.isEnglish(languageTag)) {
        stringResource(R.string.settings_language_english)
    } else {
        stringResource(R.string.settings_language_arabic)
    }

@Composable
private fun themeSubtitle(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    }
