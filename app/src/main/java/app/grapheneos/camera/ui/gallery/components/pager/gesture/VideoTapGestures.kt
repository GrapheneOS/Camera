package app.grapheneos.camera.ui.gallery.components.pager.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.LayoutDirection
import app.grapheneos.camera.ui.mediaviewer.components.video.VIDEO_SKIP_AREA_FRACTION
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection

internal suspend fun PointerInputScope.detectVideoTaps(
    layoutDirection: LayoutDirection,
    screenPositionOf: (Offset) -> Offset,
    screenWidth: () -> Float,
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onSkip: (SkipDirection, Offset) -> Unit,
) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis

    awaitEachGesture {
        var tapCount = 0
        var tap = awaitFirstTap()

        while (tap != null) {
            tapCount++
            if (tapCount > 1) {
                val screenPosition = screenPositionOf(tap)
                val width = screenWidth()
                val direction = skipDirectionAt(
                    x = screenPosition.x,
                    width = width,
                    layoutDirection = layoutDirection,
                )

                when {
                    direction != null -> onSkip(
                        direction,
                        screenPosition - skipAreaOrigin(
                            direction = direction,
                            width = width,
                            layoutDirection = layoutDirection,
                        ),
                    )
                    tapCount == 2 -> onDoubleTap(tap)
                }
            }
            tap = awaitNextTap(timeout = doubleTapTimeout)
        }

        if (tapCount == 1) {
            onTap()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitFirstTap(): Offset? {
    awaitFirstDown()

    return awaitTapUp()
}

private suspend fun AwaitPointerEventScope.awaitNextTap(timeout: Long): Offset? {
    withTimeoutOrNull(timeout) { awaitFirstDown() } ?: return null

    return awaitTapUp()
}

private suspend fun AwaitPointerEventScope.awaitTapUp(): Offset? {
    return waitForUpOrCancellation()?.also { it.consume() }?.position
}

private fun skipDirectionAt(
    x: Float,
    width: Float,
    layoutDirection: LayoutDirection,
): SkipDirection? {
    val areaWidth = width * VIDEO_SKIP_AREA_FRACTION
    val fromStart = when (layoutDirection) {
        LayoutDirection.Ltr -> x
        LayoutDirection.Rtl -> width - x
    }

    return when {
        fromStart < areaWidth -> SkipDirection.Back
        fromStart > width - areaWidth -> SkipDirection.Forward
        else -> null
    }
}

private fun skipAreaOrigin(
    direction: SkipDirection,
    width: Float,
    layoutDirection: LayoutDirection,
): Offset {
    val isOnLeft = (direction == SkipDirection.Back) == (layoutDirection == LayoutDirection.Ltr)

    return when {
        isOnLeft -> Offset.Zero
        else -> Offset(
            x = width * (1f - VIDEO_SKIP_AREA_FRACTION),
            y = 0f,
        )
    }
}
