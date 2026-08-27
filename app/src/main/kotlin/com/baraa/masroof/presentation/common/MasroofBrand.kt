package com.baraa.masroof.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baraa.masroof.R

@Composable
fun MasroofLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_masroof_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

@Composable
fun MasroofLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified,
) {
    val painter = painterResource(R.drawable.ic_masroof_logo_mark)
    if (tint == Color.Unspecified) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
        )
    } else {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}
