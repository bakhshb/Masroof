package com.baraa.masroof.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MasroofDesignTokensTest {
    @Test
    fun spacing_screenTokens_useSharedRhythm() {
        assertEquals(MasroofSpacing.screenHorizontal, MasroofSpacing.screenVertical)
        assertEquals(24, MasroofSpacing.screenPaddingLarge.value.toInt())
        assertEquals(12, MasroofSpacing.sectionGap.value.toInt())
    }

    @Test
    fun iconSizes_sectionHeader_matchesMedium() {
        assertEquals(MasroofIconSizes.md, MasroofIconSizes.sectionHeader)
    }

    @Test
    fun elevation_cardIsHigherThanNavCard() {
        assertEquals(8, MasroofElevation.card.value.toInt())
        assertEquals(6, MasroofElevation.navCard.value.toInt())
    }
}
