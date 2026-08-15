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
    val Border = Color(0xFFE5E7EB)
    val MiniBackground = Color(0xFFFAFAFA)
    val HintBackground = Color(0xFFF9FAFB)
}

object MasroofLightColors {
    val Background = Color(0xFFF4F6F8)
    val BackgroundGradientTop = Color(0xFFE8EEF3)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = MasroofPalette.Text
    val OnSurfaceVariant = MasroofPalette.Muted
    val Outline = MasroofPalette.Border
    val Primary = MasroofPalette.Primary
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFCCFBF1)
    val OnPrimaryContainer = Color(0xFF134E4A)
    val SecondaryContainer = MasroofPalette.CardSoft
    val OnSecondaryContainer = MasroofPalette.Card
    val Tertiary = MasroofPalette.Inflow
    val OnTertiary = Color(0xFFFFFFFF)
    val Error = MasroofPalette.Outflow
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = MasroofPalette.OutflowSoft
    val OnErrorContainer = Color(0xFF991B1B)
    val SurfaceVariant = Color(0xFFF3F4F6)
}

object MasroofDarkColors {
    val Background = Color(0xFF111827)
    val BackgroundGradientTop = Color(0xFF1F2937)
    val Surface = Color(0xFF1F2937)
    val OnSurface = Color(0xFFF3F4F6)
    val OnSurfaceVariant = Color(0xFF9CA3AF)
    val Outline = Color(0xFF374151)
    val Primary = Color(0xFF2DD4BF)
    val OnPrimary = Color(0xFF042F2E)
    val PrimaryContainer = Color(0xFF134E4A)
    val OnPrimaryContainer = Color(0xFFCCFBF1)
    val SecondaryContainer = Color(0xFF3B0764)
    val OnSecondaryContainer = Color(0xFFE9D5FF)
    val Tertiary = Color(0xFF34D399)
    val OnTertiary = Color(0xFF064E3B)
    val Error = Color(0xFFF87171)
    val OnError = Color(0xFF450A0A)
    val ErrorContainer = Color(0xFF7F1D1D)
    val OnErrorContainer = Color(0xFFFECACA)
    val SurfaceVariant = Color(0xFF374151)
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
        cardSurface = Color(0xFFFFFFFF),
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
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    backgroundGradientTop = MasroofLightColors.BackgroundGradientTop,
    cardShadow = Color(0x140F172A),
)

fun darkExtendedColors() = MasroofExtendedColors(
    account = Color(0xFF93C5FD),
    accountSoft = Color(0xFF1E3A5F),
    card = Color(0xFFC4B5FD),
    cardSoft = Color(0xFF4C1D95),
    liability = Color(0xFFFCD34D),
    liabilitySoft = Color(0xFF78350F),
    inflow = Color(0xFF6EE7B7),
    inflowSoft = Color(0xFF14532D),
    inflowRowBorder = Color(0xFF166534),
    outflow = Color(0xFFFCA5A5),
    outflowSoft = Color(0xFF7F1D1D),
    outflowRowBorder = Color(0xFF991B1B),
    highlight = Color(0xFF7C2D12),
    highlightBorder = Color(0xFFEA580C),
    miniBackground = Color(0xFF2B3440),
    hintBackground = Color(0xFF252F3B),
    cardSurface = MasroofDarkColors.Surface,
    cardBorder = MasroofDarkColors.Outline,
    backgroundGradientTop = MasroofDarkColors.BackgroundGradientTop,
    cardShadow = Color(0x40000000),
)
