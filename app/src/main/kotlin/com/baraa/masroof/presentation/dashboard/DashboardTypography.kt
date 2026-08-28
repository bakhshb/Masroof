package com.baraa.masroof.presentation.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.baraa.masroof.presentation.common.MasroofAmountRole
import com.baraa.masroof.presentation.common.MasroofAmountText
import com.baraa.masroof.presentation.common.MasroofTextStyles

typealias DashboardAmountRole = MasroofAmountRole
typealias DashboardTextStyles = MasroofTextStyles

@Composable
fun DashboardAmountText(
    amount: String,
    role: DashboardAmountRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    heroScale: Float = 1f,
) = MasroofAmountText(
    amount = amount,
    role = role,
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    heroScale = heroScale,
)
