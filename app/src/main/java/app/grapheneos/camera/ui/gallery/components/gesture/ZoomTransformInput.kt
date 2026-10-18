package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

internal fun Modifier.zoomTransformInput(
    gestureState: ZoomGestureState,
    onZoomedOut: () -> Unit,
): Modifier {
    return pointerInput(gestureState, onZoomedOut) {
        detectZoomTransformGestures(
            gestureState = gestureState,
            onZoomedOut = onZoomedOut,
        )
    }
}

private suspend fun PointerInputScope.detectZoomTransformGestures(
    gestureState: ZoomGestureState,
    onZoomedOut: () -> Unit,
) {
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        var accumulatedZoom = 1f
        var accumulatedPan = Offset.Zero
        var isTransformActive = false
        var pressedPointerCount: Int

        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            pressedPointerCount = event.changes.count { it.pressed }
            val zoomChange = when {
                pressedPointerCount > 1 -> event.calculateZoom()
                else -> 1f
            }
            val panChange = when {
                gestureState.isZoomed() -> event.calculatePan()
                else -> Offset.Zero
            }

            if (!isTransformActive) {
                val zoomMotion = abs(1f - accumulatedZoom) *
                    event.calculateCentroidSize(useCurrent = false)
                val isPinching = pressedPointerCount > 1 && zoomMotion > touchSlop
                val isPanning = gestureState.isZoomed() && accumulatedPan.getDistance() > touchSlop

                accumulatedZoom *= zoomChange
                accumulatedPan += panChange
                isTransformActive = isPinching || isPanning
            }

            if (isTransformActive) {
                applyTransform(
                    event = event,
                    gestureState = gestureState,
                    zoomChange = zoomChange,
                    panChange = panChange,
                    onZoomedOut = onZoomedOut,
                )
            }
        } while (pressedPointerCount > 0)
    }
}

private fun applyTransform(
    event: PointerEvent,
    gestureState: ZoomGestureState,
    zoomChange: Float,
    panChange: Offset,
    onZoomedOut: () -> Unit,
) {
    val wasZoomed = gestureState.isZoomed()
    gestureState.applyTransform(
        centroid = event.calculateCentroid(useCurrent = false),
        zoomChange = zoomChange,
        panChange = panChange,
    )

    if (wasZoomed && !gestureState.isZoomed()) {
        onZoomedOut()
    }

    event.changes
        .filter { it.positionChanged() }
        .forEach { it.consume() }
}
