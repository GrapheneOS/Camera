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
        val tracker = ZoomTransformTracker(
            gestureState = gestureState,
            touchSlop = touchSlop,
            onZoomedOut = onZoomedOut,
        )

        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val isDismissing = event.changes.any { it.isConsumed }
            val isTracking = !isDismissing && event.changes.any { it.pressed }

            if (isTracking) {
                tracker.track(event)
            }
        } while (isTracking)
    }
}

private class ZoomTransformTracker(
    private val gestureState: ZoomGestureState,
    private val touchSlop: Float,
    private val onZoomedOut: () -> Unit,
) {

    private var accumulatedZoom = 1f
    private var accumulatedPan = Offset.Zero
    private var isTransformActive = false

    fun track(event: PointerEvent) {
        val isPinch = event.changes.count { it.pressed } > 1
        val zoomChange = when {
            isPinch -> event.calculateZoom()
            else -> 1f
        }
        val panChange = when {
            gestureState.isZoomed() -> event.calculatePan()
            else -> Offset.Zero
        }

        if (!isTransformActive) {
            accumulatedZoom *= zoomChange
            accumulatedPan += panChange
            isTransformActive = isPastSlop(
                event = event,
                isPinch = isPinch,
            )
        }

        if (isTransformActive) {
            applyTransform(
                event = event,
                zoomChange = zoomChange,
                panChange = panChange,
            )
        }
    }

    private fun isPastSlop(
        event: PointerEvent,
        isPinch: Boolean,
    ): Boolean {
        val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
        val isPinching = isPinch && zoomMotion > touchSlop
        val isPanning = gestureState.isZoomed() && accumulatedPan.getDistance() > touchSlop

        return isPinching || isPanning
    }

    private fun applyTransform(
        event: PointerEvent,
        zoomChange: Float,
        panChange: Offset,
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

        event.consumePositionChanges()
    }
}
