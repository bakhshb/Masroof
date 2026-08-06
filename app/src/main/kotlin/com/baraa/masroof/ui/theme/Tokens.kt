package com.baraa.masroof.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized financial design tokens.
 *
 * Colors:
 * - Navy primary (#142B4A) communicates financial trust.
 * - Emerald secondary (#087F6D) is reserved for primary actions and
 *   confirmed states.
 * - Expense red, income green, amber warning, informational blue each
 *   carry a single semantic meaning and never serve as the dominant
 *   application accent.
 */
object FinancialPalette {
    // Brand
    val NavyPrimary = Color(0xFF142B4A)
    val NavyContainer = Color(0xFFE8EEF6)
    val EmeraldSecondary = Color(0xFF087F6D)
    val EmeraldContainer = Color(0xFFDDF4EF)

    // Surfaces
    val Background = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val ElevatedSurface = Color(0xFFFAFBFC)
    val OutlineColor = Color(0xFFD9DEE7)
    val DividerColor = Color(0xFFE4E7EC)

    // Text
    val PrimaryText = Color(0xFF172033)
    val SecondaryText = Color(0xFF667085)
    val InvertedText = Color(0xFFFFFFFF)

    // Semantic
    val Positive = Color(0xFF16804A)
    val PositiveContainer = Color(0xFFD1FADF)
    val Expense = Color(0xFFB42318)
    val ExpenseContainer = Color(0xFFFEE4E2)
    val Warning = Color(0xFFB7791F)
    val WarningContainer = Color(0xFFFEF0C7)
    val Informational = Color(0xFF2563EB)
    val InformationalContainer = Color(0xFFE0F2FE)
    val Disabled = Color(0xFF98A2B3)

    // Dark
    val DarkBackground = Color(0xFF0B1019)
    val DarkSurface = Color(0xFF121A29)
    val DarkElevated = Color(0xFF182236)
    val DarkNavyContainer = Color(0xFF1B2D4E)
    val DarkEmeraldContainer = Color(0xFF134A41)
    val DarkPrimaryText = Color(0xFFE6EAF2)
    val DarkSecondaryText = Color(0xFF98A2B3)
    val DarkOutline = Color(0xFF2C3851)
    val DarkDivider = Color(0xFF1E2A40)
    val DarkPositive = Color(0xFF4ADE80)
    val DarkExpense = Color(0xFFFCA5A5)
    val DarkWarning = Color(0xFFFAC56B)
    val DarkInformational = Color(0xFF93C5FD)
}

/** Distinct series colors for dashboard charts (light theme). */
object ChartSeriesColors {
    val Income = FinancialPalette.Positive
    val Expenses = FinancialPalette.Expense
    val BankFees = Color(0xFFB7791F)
    val Refunds = FinancialPalette.Informational
    val Investments = FinancialPalette.EmeraldSecondary
    val Transfers = FinancialPalette.NavyPrimary
    val Liquidity = FinancialPalette.EmeraldSecondary

    val DarkIncome = FinancialPalette.DarkPositive
    val DarkExpenses = FinancialPalette.DarkExpense
    val DarkBankFees = FinancialPalette.DarkWarning
    val DarkRefunds = FinancialPalette.DarkInformational
    val DarkInvestments = Color(0xFF5EEAD4)
    val DarkTransfers = Color(0xFF93C5FD)
    val DarkLiquidity = Color(0xFF5EEAD4)
}

/** Spacing tokens. Use these instead of arbitrary dp values. */
object Spacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val touch = 48.dp
}

/** Shape tokens. */
object FinancialShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(50)
}

/** Typography scale tuned for Arabic financial content. */
object FinancialTypography {
    val heroValue = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp)
    val financialTotal = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
    val sectionTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val merchant = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val metadata = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val supportingLabel = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val badge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    val button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
}

/** Local exposing the typography mapping outside of material's defaults. */
@Immutable
data class FinancialTypographyHolder(val typography: Map<FinancialTextStyle, TextStyle>)

enum class FinancialTextStyle { HeroValue, FinancialTotal, SectionTitle, Merchant, Metadata, SupportingLabel, Badge, Button }

val LocalFinancialTypography = compositionLocalOf {
    FinancialTypographyHolder(
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
    )
}

/** Returns the [TextStyle] associated with the financial-text style. */
@Composable
fun financialTextStyle(style: FinancialTextStyle): TextStyle =
    LocalFinancialTypography.current.typography[style] ?: FinancialTypography.merchant

/** Conversion helper so existing material typography keeps working. */
fun typographyHolderAsMaterial(holder: FinancialTypographyHolder): Typography = Typography(
    displayLarge = holder.typography[FinancialTextStyle.HeroValue] ?: TextStyle.Default,
    displayMedium = holder.typography[FinancialTextStyle.FinancialTotal] ?: TextStyle.Default,
    titleLarge = holder.typography[FinancialTextStyle.SectionTitle] ?: TextStyle.Default,
    titleMedium = holder.typography[FinancialTextStyle.Merchant] ?: TextStyle.Default,
    labelLarge = holder.typography[FinancialTextStyle.Button] ?: TextStyle.Default,
    bodySmall = holder.typography[FinancialTextStyle.Metadata] ?: TextStyle.Default,
)
