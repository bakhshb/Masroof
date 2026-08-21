package com.baraa.masroof.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasroofExtendedColorsTest {
    @Test
    fun darkExtendedColors_useDarkSurfaces() {
        val light = lightExtendedColors()
        val dark = darkExtendedColors()

        assertNotEquals(light.cardSurface, dark.cardSurface)
        assertNotEquals(light.miniBackground, dark.miniBackground)
        assertNotEquals(light.backgroundGradientTop, dark.backgroundGradientTop)

        assertTrue(isDark(dark.cardSurface))
        assertTrue(isDark(dark.miniBackground))
        assertTrue(isLight(light.cardSurface))
    }

    @Test
    fun darkExtendedColors_keepSemanticContrast() {
        val dark = darkExtendedColors()
        assertTrue(dark.inflow.red + dark.inflow.green + dark.inflow.blue > 0.5f)
        assertTrue(dark.outflow.red + dark.outflow.green + dark.outflow.blue > 0.5f)
    }

    private fun isDark(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance < 0.5f
    }

    private fun isLight(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance > 0.8f
    }
}
