package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baraa.masroof.domain.model.CardNetwork

@Composable
fun CardNetworkBadge(
    network: CardNetwork?,
    last4: String,
    modifier: Modifier = Modifier,
) {
    val colors = networkColors(network, last4)
    Box(
        modifier = modifier
            .size(width = 36.dp, height = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(brush = Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            networkAbbreviation(network),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

private fun networkColors(network: CardNetwork?, last4: String): List<Color> =
    when (network) {
        CardNetwork.MADA -> listOf(Color(0xFF004D40), Color(0xFF00695C))
        CardNetwork.VISA -> listOf(Color(0xFF1A1F71), Color(0xFF1A1F71))
        CardNetwork.MASTERCARD -> listOf(Color(0xFFEB001B), Color(0xFFF79E1B))
        CardNetwork.AMEX -> listOf(Color(0xFF006FCF), Color(0xFF006FCF))
        CardNetwork.UNKNOWN, null -> when (last4.firstOrNull()?.digitToIntOrNull()?.rem(3)) {
            0 -> listOf(Color(0xFFEB001B), Color(0xFFF79E1B))
            1 -> listOf(Color(0xFF1A1F71), Color(0xFF1A1F71))
            else -> listOf(Color(0xFF004D40), Color(0xFF00695C))
        }
    }

private fun networkAbbreviation(network: CardNetwork?): String =
    when (network) {
        CardNetwork.MADA -> "MD"
        CardNetwork.VISA -> "VI"
        CardNetwork.MASTERCARD -> "MC"
        CardNetwork.AMEX -> "AX"
        CardNetwork.UNKNOWN, null -> "••"
    }
