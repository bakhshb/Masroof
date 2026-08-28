package com.baraa.masroof.presentation.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class MasroofColorContrastTest {
    @Test
    fun lightPrimaryContainer_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofLightColors.OnPrimaryContainer,
                MasroofLightColors.PrimaryContainer,
            ),
        )
    }

    @Test
    fun lightPrimary_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofLightColors.OnPrimary,
                MasroofLightColors.Primary,
            ),
        )
    }

    @Test
    fun darkPrimaryContainer_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofDarkColors.OnPrimaryContainer,
                MasroofDarkColors.PrimaryContainer,
            ),
        )
    }

    @Test
    fun darkPrimary_supportsLargeText() {
        assertTrue(
            meetsWcagAaLargeText(
                MasroofDarkColors.OnPrimary,
                MasroofDarkColors.Primary,
            ),
        )
    }

    @Test
    fun darkOnSurface_supportsNormalTextOnCardSurface() {
        assertTrue(
            meetsWcagAaNormalText(
                MasroofDarkColors.OnSurface,
                MasroofDarkColors.SurfaceContainer,
            ),
        )
    }

    @Test
    fun darkSpending_supportsLargeTextOnSoftBackground() {
        val dark = darkExtendedColors()
        assertTrue(meetsWcagAaLargeText(dark.outflow, dark.outflowSoft))
    }
}
