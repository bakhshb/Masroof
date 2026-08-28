package com.baraa.masroof.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object MasroofPalette {
    val Primary = Color(0xFF0F766E)
    val Account = Color(0xFF1D4ED8)
    val AccountSoft = Color(0xFFEFF6FF)
    val Card = Color(0xFF7C3AED)
    val CardSoft = Color(0xFFF5F3FF)
    val Liability = Color(0xFFB45309)
    val LiabilitySoft = Color(0xFFFFFBEB)
    val Inflow = Color(0xFF059669)
    val InflowSoft = Color(0xFFECFDF5)
    val Outflow = Color(0xFFDC2626)
    val OutflowSoft = Color(0xFFFEF2F2)
    val Highlight = Color(0xFFFFF7ED)
    val HighlightBorder = Color(0xFFFED7AA)
    val Text = Color(0xFF1A1F24)
    val Muted = Color(0xFF6B7280)
    val Border = Color(0xFFD1D5DB)
    val MiniBackground = Color(0xFFFAFAFA)
    val HintBackground = Color(0xFFF9FAFB)
}

object MasroofLightColors {
    val Background = Color(0xFFF4F6F8)
    val BackgroundGradientTop = Color(0xFFE8EEF3)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDim = Color(0xFFEEF2F5)
    val SurfaceBright = Color(0xFFFFFFFF)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFF7F9FA)
    val SurfaceContainer = Color(0xFFF4F6F8)
    val SurfaceContainerHigh = Color(0xFFEEF2F5)
    val SurfaceContainerHighest = Color(0xFFE8EEF3)
    val OnSurface = MasroofPalette.Text
    val OnSurfaceVariant = MasroofPalette.Muted
    val Outline = MasroofPalette.Border
    val OutlineVariant = Color(0xFFE5E7EB)
    val Primary = MasroofPalette.Primary
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF5EEAD4)
    val OnPrimaryContainer = Color(0xFF042F2E)
    val SecondaryContainer = MasroofPalette.CardSoft
    val OnSecondaryContainer = MasroofPalette.Card
    val Tertiary = MasroofPalette.Inflow
    val OnTertiary = Color(0xFFFFFFFF)
    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)
    val SurfaceVariant = Color(0xFFE8EEF3)
}

object MasroofDarkColors {
    val Background = Color(0xFF121212)
    val BackgroundGradientTop = Color(0xFF1A1A1A)
    val Surface = Color(0xFF121212)
    val SurfaceDim = Color(0xFF0F0F0F)
    val SurfaceBright = Color(0xFF1A1A1A)
    val SurfaceContainerLowest = Color(0xFF0F0F0F)
    val SurfaceContainerLow = Color(0xFF1A1A1A)
    val SurfaceContainer = Color(0xFF1E1E1E)
    val SurfaceContainerHigh = Color(0xFF282828)
    val SurfaceContainerHighest = Color(0xFF323232)
    val OnSurface = Color(0xFFE3E3E3)
    val OnSurfaceVariant = Color(0xFFA8A8A8)
    val Outline = Color(0xFF454545)
    val OutlineVariant = Color(0xFF323232)
    val Primary = Color(0xFF5EBDB2)
    val OnPrimary = Color(0xFF042F2E)
    val PrimaryContainer = Color(0xFF0F766E)
    val OnPrimaryContainer = Color(0xFFCCFBF1)
    val SecondaryContainer = Color(0xFF2E1065)
    val OnSecondaryContainer = Color(0xFFE9D5FF)
    val Tertiary = Color(0xFF6EE7B7)
    val OnTertiary = Color(0xFF064E3B)
    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)
    val ErrorContainer = Color(0xFF93000A)
    val OnErrorContainer = Color(0xFFFFB4AB)
    val SurfaceVariant = SurfaceContainer
}

@Immutable
data class MasroofExtendedColors(
    val account: Color,
    val accountSoft: Color,
    val card: Color,
    val cardSoft: Color,
    val liability: Color,
    val liabilitySoft: Color,
    val inflow: Color,
    val inflowSoft: Color,
    val inflowRowBorder: Color,
    val outflow: Color,
    val outflowSoft: Color,
    val outflowRowBorder: Color,
    val highlight: Color,
    val highlightBorder: Color,
    val miniBackground: Color,
    val hintBackground: Color,
    val cardSurface: Color,
    val navCardSurface: Color,
    val navCardBorder: Color,
    val navCardShadow: Color,
    val cardBorder: Color,
    val backgroundGradientTop: Color,
    val cardShadow: Color,
)

val LocalMasroofExtendedColors = staticCompositionLocalOf {
    MasroofExtendedColors(
        account = MasroofPalette.Account,
        accountSoft = MasroofPalette.AccountSoft,
        card = MasroofPalette.Card,
        cardSoft = MasroofPalette.CardSoft,
        liability = MasroofPalette.Liability,
        liabilitySoft = MasroofPalette.LiabilitySoft,
        inflow = MasroofPalette.Inflow,
        inflowSoft = MasroofPalette.InflowSoft,
        inflowRowBorder = Color(0xFFBBF7D0),
        outflow = MasroofPalette.Outflow,
        outflowSoft = MasroofPalette.OutflowSoft,
        outflowRowBorder = Color(0xFFFECACA),
        highlight = MasroofPalette.Highlight,
        highlightBorder = MasroofPalette.HighlightBorder,
        miniBackground = Color(0xFFF8FAFC),
        hintBackground = MasroofPalette.HintBackground,
        cardSurface = MasroofLightColors.Surface,
        navCardSurface = MasroofLightColors.Surface,
        navCardBorder = MasroofLightColors.Outline,
        navCardShadow = Color(0x220F172A),
        cardBorder = Color(0xFFE2E8F0),
        backgroundGradientTop = MasroofLightColors.BackgroundGradientTop,
        cardShadow = Color(0x140F172A),
    )
}

fun lightExtendedColors() = MasroofExtendedColors(
    account = MasroofPalette.Account,
    accountSoft = MasroofPalette.AccountSoft,
    card = MasroofPalette.Card,
    cardSoft = MasroofPalette.CardSoft,
    liability = MasroofPalette.Liability,
    liabilitySoft = MasroofPalette.LiabilitySoft,
    inflow = MasroofPalette.Inflow,
    inflowSoft = Color(0xFFF0FDF4),
    inflowRowBorder = Color(0xFFBBF7D0),
    outflow = MasroofPalette.Outflow,
    outflowSoft = Color(0xFFFEF2F2),
    outflowRowBorder = Color(0xFFFECACA),
    highlight = MasroofPalette.Highlight,
    highlightBorder = MasroofPalette.HighlightBorder,
    miniBackground = Color(0xFFF8FAFC),
    hintBackground = Color(0xFFF8FAFC),
    cardSurface = MasroofLightColors.Surface,
    navCardSurface = MasroofLightColors.Surface,
    navCardBorder = MasroofLightColors.Outline,
    navCardShadow = Color(0x220F172A),
    cardBorder = Color(0xFFE2E8F0),
    backgroundGradientTop = MasroofLightColors.BackgroundGradientTop,
    cardShadow = Color(0x140F172A),
)

fun darkExtendedColors() = MasroofExtendedColors(
    account = Color(0xFF93C5FD),
    accountSoft = Color(0xFF1A2744),
    card = Color(0xFFC4B5FD),
    cardSoft = Color(0xFF2E1065),
    liability = Color(0xFFFCD34D),
    liabilitySoft = Color(0xFF422006),
    inflow = Color(0xFF6EE7B7),
    inflowSoft = Color(0xFF0F3D2E),
    inflowRowBorder = Color(0xFF166534),
    outflow = Color(0xFFFCA5A5),
    outflowSoft = Color(0xFF4A1515),
    outflowRowBorder = Color(0xFF991B1B),
    highlight = Color(0xFF7C2D12),
    highlightBorder = Color(0xFFEA580C),
    miniBackground = MasroofDarkColors.SurfaceContainerLow,
    hintBackground = MasroofDarkColors.SurfaceContainerLow,
    cardSurface = MasroofDarkColors.SurfaceContainer,
    navCardSurface = MasroofDarkColors.SurfaceContainerHigh,
    navCardBorder = MasroofDarkColors.Outline,
    navCardShadow = Color(0x40000000),
    cardBorder = MasroofDarkColors.Outline,
    backgroundGradientTop = MasroofDarkColors.BackgroundGradientTop,
    cardShadow = Color(0x40000000),
)
