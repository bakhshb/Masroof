package com.baraa.masroof.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware semantic colors. Prefer these over raw [FinancialPalette]
 * light tokens so chips, banners, and money values remain readable in
 * both light and dark schemes.
 */
object SemanticColors {
    @Composable
    fun positive(): Color =
        if (isDark()) FinancialPalette.DarkPositive else FinancialPalette.Positive

    @Composable
    fun positiveContainer(): Color =
        if (isDark()) FinancialPalette.DarkEmeraldContainer else FinancialPalette.PositiveContainer

    @Composable
    fun expense(): Color =
        if (isDark()) FinancialPalette.DarkExpense else FinancialPalette.Expense

    @Composable
    fun expenseContainer(): Color =
        if (isDark()) Color(0xFF3F1D1D) else FinancialPalette.ExpenseContainer

    @Composable
    fun warning(): Color =
        if (isDark()) FinancialPalette.DarkWarning else FinancialPalette.Warning

    @Composable
    fun warningContainer(): Color =
        if (isDark()) Color(0xFF3D2E12) else FinancialPalette.WarningContainer

    @Composable
    fun informational(): Color =
        if (isDark()) FinancialPalette.DarkInformational else FinancialPalette.Informational

    @Composable
    fun informationalContainer(): Color =
        if (isDark()) FinancialPalette.DarkNavyContainer else FinancialPalette.InformationalContainer

    @Composable
    fun brandContainer(): Color = MaterialTheme.colorScheme.primaryContainer

    @Composable
    fun onBrandContainer(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    fun secondaryAccent(): Color = MaterialTheme.colorScheme.secondary

    @Composable
    private fun isDark(): Boolean {
        // Prefer Material scheme background luminance over system flag so a
        // forced LIGHT/DARK preference is honored even when OS differs.
        val bg = MaterialTheme.colorScheme.background
        return bg.luminance() < 0.5f
    }
}

private fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
