package com.baraa.masroof.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/** Default pull distance before refresh can trigger on release (~2× a typical short pull). */
val LongPullToRefreshThreshold: Dp = 160.dp

/**
 * Pull-to-refresh wrapper with a high threshold and release-to-confirm behavior:
 * drag down, optionally hold, then lift to refresh. Pulling back up before release cancels.
 */
@Composable
fun LongPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    threshold: Dp = LongPullToRefreshThreshold,
    content: @Composable BoxScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val maxPullPx = thresholdPx * 1.25f
    val isRefreshingState = rememberUpdatedState(isRefreshing)
    val onRefreshState = rememberUpdatedState(onRefresh)

    val nestedScrollConnection = remember(scrollState, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistance > 0f && available.y < 0f) {
                    val consumed = min(-available.y, pullDistance)
                    pullDistance -= consumed
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isRefreshingState.value && scrollState.value == 0 && available.y > 0f) {
                    pullDistance = min(pullDistance + available.y, maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isRefreshingState.value && pullDistance > 0f && pullDistance < thresholdPx) {
                    pullDistance = 0f
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullDistance = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(thresholdPx) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })

                    if (!isRefreshingState.value) {
                        if (pullDistance >= thresholdPx) {
                            onRefreshState.value()
                        }
                        pullDistance = 0f
                    }
                }
            }
            .nestedScroll(nestedScrollConnection),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .then(
                    if (pullDistance > 0f) {
                        Modifier.offset { IntOffset(0, pullDistance.roundToInt()) }
                    } else {
                        Modifier
                    },
                ),
            content = content,
        )

        if (pullDistance > 0f || isRefreshing) {
            val isReady = pullDistance >= thresholdPx
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .alpha(
                        when {
                            isRefreshing -> 1f
                            isReady -> 1f
                            else -> min(1f, pullDistance / thresholdPx)
                        },
                    ),
                color = if (isReady && !isRefreshing) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
