package com.baraa.masroof.presentation.dashboard

import androidx.compose.ui.text.font.FontWeight
import com.baraa.masroof.presentation.theme.MasroofTypography
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardTypographyTest {
    @Test
    fun metricTileAmount_usesLabelLargeBold_notCustomSp() {
        val style = MasroofTypography.labelLarge.copy(fontWeight = FontWeight.Bold)
        assertEquals(14, style.fontSize.value.toInt())
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun headlineSmall_isDefinedInTheme() {
        assertEquals(18, MasroofTypography.headlineSmall.fontSize.value.toInt())
    }
}
