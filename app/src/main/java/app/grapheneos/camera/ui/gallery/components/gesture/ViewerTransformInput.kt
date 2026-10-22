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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlin.math.min

internal fun Modifier.viewerGestureInput(gestures: ViewerGestures): Modifier {
    return pointerInput(gestures) {
        detectViewerGestures(gestures)
    }
}

private suspend fun PointerInputScope.detectViewerGestures(gestures: ViewerGestures) {
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val tracker = GestureStartTracker(
            touchSlop = touchSlop,
            minScalingSpan = gestures.minScalingSpan,
            isZoomed = gestures.transformState.isZoomed(),
            showsDetails = gestures.detailsState.isShown(),
        )
        var event: PointerEvent

        do {
            event = awaitPointerEvent(pass = PointerEventPass.Initial)
            tracker.track(event)
        } while (tracker.isUndecided && event.changes.any { it.pressed })

        val start = tracker.start
        if (start != null && !gestures.transformState.isDismissed) {
            event.consumePositionChanges()

            when (start) {
                GestureStart.Details -> dragDetails(gestures)
                else -> transformPhoto(
                    gestures = gestures,
                    start = start,
                )
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.transformPhoto(
    gestures: ViewerGestures,
    start: GestureStart,
) {
    val transformState = gestures.transformState

    when (start) {
        GestureStart.Dismiss -> transformState.startDrag()
        else -> transformState.startTransform()
    }

    try {
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val wasZoomed = transformState.isZoomed()
            val wasAtLimit = transformState.isZoomStoppedAtLimit()
            val isScaling = event.hasScalingSpan(gestures.minScalingSpan)

            transformState.applyTransform(
                centroid = event.calculateCentroid(useCurrent = false),
                panChange = event.calculatePan(),
                zoomChange = event.calculateZoom().takeIf { isScaling } ?: 1f,
                rotationChange = event.calculateRotation().takeIf { isScaling } ?: 0f,
            )

            val reachesFit = wasZoomed && transformState.isZoomStoppedAtFit()
            val reachesLimit = !wasAtLimit && transformState.isZoomStoppedAtLimit()

            if (reachesFit || reachesLimit) {
                gestures.hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
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

private suspend fun AwaitPointerEventScope.dragDetails(gestures: ViewerGestures) {
    val detailsState = gestures.detailsState
    val velocityTracker = VelocityTracker()

    detailsState.startDrag()
    gestures.onDetailsShownChange(true)

    try {
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)

            event.changes.firstOrNull()?.let(velocityTracker::addPointerInputChange)
            detailsState.drag(event.calculatePan().y)
            event.consumePositionChanges()
        } while (event.changes.any { it.pressed })
    } finally {
        val opens = detailsState.release(velocity = velocityTracker.calculateVelocity().y)

        gestures.onDetailsShownChange(opens)
    }
}

private enum class GestureStart {
    Dismiss,
    Transform,
    Details,
}

private class GestureStartTracker(
    private val touchSlop: Float,
    private val minScalingSpan: Float,
    private val isZoomed: Boolean,
    private val showsDetails: Boolean,
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

        val isPinched = isPinch && zoomMotion(event) > touchSlop
        val isPanned = accumulatedPan.getDistance() > touchSlop
        val isDraggedWithOneFinger = isPanned && !isPinch

        start = startOf(
            isPinched = isPinched,
            isPanned = isPanned,
            isDraggedWithOneFinger = isDraggedWithOneFinger,
        )
        isUndecided = start == null && !isDraggedWithOneFinger
    }

    private fun startOf(
        isPinched: Boolean,
        isPanned: Boolean,
        isDraggedWithOneFinger: Boolean,
    ): GestureStart? {
        val isVertical = abs(accumulatedPan.y) >= abs(accumulatedPan.x)
        val isDraggedVertically = isDraggedWithOneFinger && isVertical

        return when {
            showsDetails -> GestureStart.Details.takeIf { isDraggedVertically }
            isPinched || (isPanned && isZoomed) -> GestureStart.Transform
            isDraggedVertically && accumulatedPan.y > 0f -> GestureStart.Dismiss
            isDraggedVertically -> GestureStart.Details
            else -> null
        }
    }

    private fun zoomMotion(event: PointerEvent): Float {
        return abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
    }
}

private fun PointerEvent.hasScalingSpan(minScalingSpan: Float): Boolean {
    val span = 2f * min(
        calculateCentroidSize(useCurrent = false),
        calculateCentroidSize(useCurrent = true),
    )

    return span >= minScalingSpan
}
