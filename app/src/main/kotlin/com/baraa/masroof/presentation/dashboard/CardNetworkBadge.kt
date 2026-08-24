package com.baraa.masroof.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baraa.masroof.R
import com.baraa.masroof.domain.model.CardNetwork

@Composable
fun CardNetworkBadge(
    network: CardNetwork?,
    last4: String,
    modifier: Modifier = Modifier.size(width = 52.dp, height = 32.dp),
) {
    val shape = RoundedCornerShape(8.dp)
    val description = networkContentDescription(network)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(brush = Brush.linearGradient(networkBackground(network, last4)))
                .then(
                    if (description != null) {
                        Modifier.semantics { contentDescription = description }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (network) {
                CardNetwork.MASTERCARD -> MastercardMark(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )

                CardNetwork.VISA -> BrandWordmark(
                    text = cardNetworkWordmark(network),
                    italic = true,
                    letterSpacingEm = 0.04f,
                )

                CardNetwork.MADA -> BrandWordmark(
                    text = cardNetworkWordmark(network),
                    italic = false,
                    letterSpacingEm = 0.06f,
                )

                CardNetwork.AMEX -> BrandWordmark(
                    text = cardNetworkWordmark(network),
                    italic = false,
                    letterSpacingEm = 0.02f,
                )

                CardNetwork.UNKNOWN, null -> BrandWordmark(
                    text = cardNetworkWordmark(network),
                    italic = false,
                    letterSpacingEm = 0f,
                )
            }
        }
    }
}

@Composable
private fun BrandWordmark(
    text: String,
    italic: Boolean,
    letterSpacingEm: Float,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Black,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontSize = 11.sp,
            letterSpacing = letterSpacingEm.sp,
            color = Color.White,
        ),
        maxLines = 1,
    )
}

@Composable
private fun MastercardMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2.15f
        val centerY = size.height / 2f
        val overlap = radius * 0.62f
        drawCircle(
            color = Color(0xFFEB001B),
            radius = radius,
            center = Offset(x = size.width / 2f - overlap, y = centerY),
        )
        drawCircle(
            color = Color(0xFFF79E1B),
            radius = radius,
            center = Offset(x = size.width / 2f + overlap, y = centerY),
        )
    }
}

fun cardNetworkWordmark(network: CardNetwork?): String =
    when (network) {
        CardNetwork.MADA -> "mada"
        CardNetwork.VISA -> "VISA"
        CardNetwork.MASTERCARD -> "MC"
        CardNetwork.AMEX -> "AMEX"
        CardNetwork.UNKNOWN, null -> "CARD"
    }

@Composable
private fun networkContentDescription(network: CardNetwork?): String? =
    when (network) {
        CardNetwork.MADA -> stringResource(R.string.card_network_mada)
        CardNetwork.VISA -> stringResource(R.string.card_network_visa)
        CardNetwork.MASTERCARD -> stringResource(R.string.card_network_mastercard)
        CardNetwork.AMEX -> stringResource(R.string.card_network_amex)
        CardNetwork.UNKNOWN, null -> null
    }

private fun networkBackground(network: CardNetwork?, last4: String): List<Color> =
    when (network) {
        CardNetwork.MADA -> listOf(Color(0xFF007A3D), Color(0xFF00A651))
        CardNetwork.VISA -> listOf(Color(0xFF1A1F71), Color(0xFF2B3494))
        CardNetwork.MASTERCARD -> listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2C))
        CardNetwork.AMEX -> listOf(Color(0xFF006FCF), Color(0xFF00A3E0))
        CardNetwork.UNKNOWN, null -> when (last4.firstOrNull()?.digitToIntOrNull()?.rem(3)) {
            0 -> listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2C))
            1 -> listOf(Color(0xFF1A1F71), Color(0xFF2B3494))
            else -> listOf(Color(0xFF007A3D), Color(0xFF00A651))
        }
    }
