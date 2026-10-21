package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.min

internal fun Modifier.viewerTransformInput(
    transformState: ViewerTransformState,
    minScalingSpan: Float,
    hapticFeedback: HapticFeedback,
): Modifier {
    return pointerInput(transformState, minScalingSpan, hapticFeedback) {
        detectViewerTransformGestures(
            transformState = transformState,
            minScalingSpan = minScalingSpan,
            hapticFeedback = hapticFeedback,
        )
    }
}

private suspend fun PointerInputScope.detectViewerTransformGestures(
    transformState: ViewerTransformState,
    minScalingSpan: Float,
    hapticFeedback: HapticFeedback,
) {
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val tracker = GestureStartTracker(
            touchSlop = touchSlop,
            minScalingSpan = minScalingSpan,
            isZoomed = transformState.isZoomed(),
        )
        var event: PointerEvent

        do {
            event = awaitPointerEvent(pass = PointerEventPass.Initial)
            tracker.track(event)
        } while (tracker.isUndecided && event.changes.any { it.pressed })

        val start = tracker.start
        if (start != null && !transformState.isDismissed) {
            event.consumePositionChanges()
            transform(
                transformState = transformState,
                start = start,
                minScalingSpan = minScalingSpan,
                hapticFeedback = hapticFeedback,
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.transform(
    transformState: ViewerTransformState,
    start: GestureStart,
    minScalingSpan: Float,
    hapticFeedback: HapticFeedback,
) {
    when (start) {
        GestureStart.Drag -> transformState.startDrag()
        GestureStart.Transform -> transformState.startTransform()
    }

    try {
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val wasZoomed = transformState.isZoomed()
            val isScaling = event.hasScalingSpan(minScalingSpan)
            transformState.applyTransform(
                centroid = event.calculateCentroid(useCurrent = false),
                panChange = event.calculatePan(),
                zoomChange = event.calculateZoom().takeIf { isScaling } ?: 1f,
                rotationChange = event.calculateRotation().takeIf { isScaling } ?: 0f,
            )

            if (wasZoomed && transformState.isZoomStoppedAtFit()) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            event.consumePositionChanges()
        } while (event.changes.any { it.pressed })

        transformState.release()
    } finally {
        if (transformState.isTransforming) {
            transformState.cancel()
        }
    }
}

private enum class GestureStart {
    Drag,
    Transform,
}

private class GestureStartTracker(
    private val touchSlop: Float,
    private val minScalingSpan: Float,
    private val isZoomed: Boolean,
) {

    var isUndecided = true
        private set

    var start: GestureStart? = null
        private set

    private var accumulatedPan = Offset.Zero
    private var accumulatedZoom = 1f

    fun track(event: PointerEvent) {
        val isPinch = event.changes.count { it.pressed } > 1
        accumulatedPan += event.calculatePan()

        if (event.hasScalingSpan(minScalingSpan)) {
            accumulatedZoom *= event.calculateZoom()
        }

        val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
        val isPinched = isPinch && zoomMotion > touchSlop
        val isPanned = accumulatedPan.getDistance() > touchSlop
        val isDraggedWithOneFinger = isPanned && !isPinch
        val isDraggedDown = isDraggedWithOneFinger && accumulatedPan.y >= abs(accumulatedPan.x)

        start = when {
            isPinched || (isPanned && isZoomed) -> GestureStart.Transform
            isDraggedDown -> GestureStart.Drag
            else -> null
        }
        isUndecided = start == null && !isDraggedWithOneFinger
    }
}

private fun PointerEvent.hasScalingSpan(minScalingSpan: Float): Boolean {
    val span = 2f * min(
        calculateCentroidSize(useCurrent = false),
        calculateCentroidSize(useCurrent = true),
    )

    return span >= minScalingSpan
}
