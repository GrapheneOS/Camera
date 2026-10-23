package app.grapheneos.camera.ui.gallery.components.pager.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.LayoutDirection
import app.grapheneos.camera.ui.mediaviewer.components.video.VIDEO_SKIP_AREA_FRACTION
import app.grapheneos.camera.ui.mediaviewer.playback.SkipDirection

internal suspend fun PointerInputScope.detectVideoTaps(
    layoutDirection: LayoutDirection,
    isSkipping: () -> Boolean,
    onTap: () -> Unit,
    onSkip: (SkipDirection, Offset) -> Unit,
) {
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    var skipCooldownEnd = 0L

    fun directionAt(position: Offset): SkipDirection? {
        return skipDirectionAt(
            x = position.x,
            width = size.width.toFloat(),
            layoutDirection = layoutDirection,
        )
    }

    fun skipAt(tap: PointerInputChange) {
        val direction = directionAt(tap.position) ?: return

        skipCooldownEnd = tap.uptimeMillis + doubleTapTimeout
        onSkip(direction, tap.position)
    }

    awaitEachGesture {
        awaitFirstDown()
        val tap = awaitTap() ?: return@awaitEachGesture

        when {
            directionAt(tap.position) == null -> onTap()
            tap.uptimeMillis < skipCooldownEnd -> Unit
            isSkipping() -> skipAt(tap)
            else -> awaitDoubleTap(
                timeout = doubleTapTimeout,
                onSingleTap = onTap,
                onDoubleTap = ::skipAt,
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitDoubleTap(
    timeout: Long,
    onSingleTap: () -> Unit,
    onDoubleTap: (PointerInputChange) -> Unit,
) {
    val secondDown = withTimeoutOrNull(timeout) { awaitFirstDown() }

    when (secondDown) {
        null -> onSingleTap()
        else -> awaitTap()?.let(onDoubleTap)
    }
}

private suspend fun AwaitPointerEventScope.awaitTap(): PointerInputChange? {
    return waitForUpOrCancellation()?.also { it.consume() }
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

internal fun skipAreaOrigin(
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
