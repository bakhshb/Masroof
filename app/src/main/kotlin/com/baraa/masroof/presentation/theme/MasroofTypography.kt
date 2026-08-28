package com.baraa.masroof.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun masroofTextStyle(
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
) = TextStyle(
    fontFamily = MasroofFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
)

val MasroofTypography = Typography(
    headlineSmall = masroofTextStyle(FontWeight.Bold, 18, 24),
    headlineMedium = masroofTextStyle(FontWeight.Bold, 22, 28),
    titleMedium = masroofTextStyle(FontWeight.Bold, 16, 22),
    titleSmall = masroofTextStyle(FontWeight.SemiBold, 14, 20),
    bodyLarge = masroofTextStyle(FontWeight.Normal, 16, 24),
    bodyMedium = masroofTextStyle(FontWeight.Normal, 14, 20),
    bodySmall = masroofTextStyle(FontWeight.Normal, 12, 18),
    labelLarge = masroofTextStyle(FontWeight.SemiBold, 14, 18),
    labelMedium = masroofTextStyle(FontWeight.Medium, 12, 16),
    labelSmall = masroofTextStyle(FontWeight.SemiBold, 11, 14),
)
