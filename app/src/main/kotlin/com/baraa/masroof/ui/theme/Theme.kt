package com.baraa.masroof.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * The Masroof theme.
 *
 * Dynamic color is **disabled** so the brand-new navy + emerald identity
 * stays consistent across devices. Light + dark variants ship with
 * hand-tuned accessible accent ratios.
 */
private val LightColorScheme = lightColorScheme(
    primary = FinancialPalette.NavyPrimary,
    onPrimary = FinancialPalette.InvertedText,
    primaryContainer = FinancialPalette.NavyContainer,
    onPrimaryContainer = FinancialPalette.NavyPrimary,
    secondary = FinancialPalette.EmeraldSecondary,
    onSecondary = FinancialPalette.InvertedText,
    secondaryContainer = FinancialPalette.EmeraldContainer,
    onSecondaryContainer = FinancialPalette.EmeraldSecondary,
    background = FinancialPalette.Background,
    onBackground = FinancialPalette.PrimaryText,
    surface = FinancialPalette.Surface,
    onSurface = FinancialPalette.PrimaryText,
    surfaceVariant = FinancialPalette.ElevatedSurface,
    onSurfaceVariant = FinancialPalette.SecondaryText,
    outline = FinancialPalette.OutlineColor,
    outlineVariant = FinancialPalette.DividerColor,
    error = FinancialPalette.Expense,
    onError = FinancialPalette.InvertedText,
    errorContainer = FinancialPalette.ExpenseContainer,
    onErrorContainer = FinancialPalette.Expense,
    tertiary = FinancialPalette.Warning,
    onTertiary = FinancialPalette.InvertedText,
    tertiaryContainer = FinancialPalette.WarningContainer,
    onTertiaryContainer = FinancialPalette.Warning,
)

private val DarkColorScheme = darkColorScheme(
    primary = FinancialPalette.DarkPrimaryText,
    onPrimary = FinancialPalette.NavyPrimary,
    primaryContainer = FinancialPalette.DarkNavyContainer,
    onPrimaryContainer = FinancialPalette.DarkPrimaryText,
    secondary = FinancialPalette.EmeraldSecondary,
    onSecondary = FinancialPalette.DarkBackground,
    secondaryContainer = FinancialPalette.DarkEmeraldContainer,
    onSecondaryContainer = FinancialPalette.DarkPositive,
    background = FinancialPalette.DarkBackground,
    onBackground = FinancialPalette.DarkPrimaryText,
    surface = FinancialPalette.DarkSurface,
    onSurface = FinancialPalette.DarkPrimaryText,
    surfaceVariant = FinancialPalette.DarkElevated,
    onSurfaceVariant = FinancialPalette.DarkSecondaryText,
    outline = FinancialPalette.DarkOutline,
    outlineVariant = FinancialPalette.DarkDivider,
    error = FinancialPalette.DarkExpense,
    onError = FinancialPalette.DarkBackground,
    errorContainer = FinancialPalette.ExpenseContainer,
    onErrorContainer = FinancialPalette.DarkExpense,
    tertiary = FinancialPalette.DarkWarning,
    onTertiary = FinancialPalette.DarkBackground,
    tertiaryContainer = FinancialPalette.WarningContainer,
    onTertiaryContainer = FinancialPalette.DarkWarning,
)

@Composable
fun MasroofTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Dynamic color is intentionally disabled by default to keep brand identity stable. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val typography = Typography(
        displayLarge = FinancialTypography.heroValue,
        displayMedium = FinancialTypography.financialTotal,
        titleLarge = FinancialTypography.sectionTitle,
        titleMedium = FinancialTypography.merchant,
        labelLarge = FinancialTypography.button,
        bodySmall = FinancialTypography.metadata,
    )
    CompositionLocalProvider(LocalFinancialTypography provides FinancialTypographyHolder(
        mapOf(
            FinancialTextStyle.HeroValue to FinancialTypography.heroValue,
            FinancialTextStyle.FinancialTotal to FinancialTypography.financialTotal,
            FinancialTextStyle.SectionTitle to FinancialTypography.sectionTitle,
            FinancialTextStyle.Merchant to FinancialTypography.merchant,
            FinancialTextStyle.Metadata to FinancialTypography.metadata,
            FinancialTextStyle.SupportingLabel to FinancialTypography.supportingLabel,
            FinancialTextStyle.Badge to FinancialTypography.badge,
            FinancialTextStyle.Button to FinancialTypography.button,
        )
    )) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = Shapes(
                extraSmall = FinancialShapes.small,
                small = FinancialShapes.small,
                medium = FinancialShapes.medium,
                large = FinancialShapes.large,
                extraLarge = FinancialShapes.large,
            ),
            content = content,
        )
    }
}
