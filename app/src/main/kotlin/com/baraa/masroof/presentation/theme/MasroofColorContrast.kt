package com.baraa.masroof.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = relativeLuminance(foreground)
    val backgroundLuminance = relativeLuminance(background)
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

fun meetsWcagAaNormalText(foreground: Color, background: Color): Boolean =
    contrastRatio(foreground, background) >= 4.5

fun meetsWcagAaLargeText(foreground: Color, background: Color): Boolean =
    contrastRatio(foreground, background) >= 3.0

private fun relativeLuminance(color: Color): Double {
    if (color.luminance() in 0.0..1.0) {
        return color.luminance().toDouble()
    }
    fun channel(value: Float): Double {
        val normalized = value.toDouble()
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}
