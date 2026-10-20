package app.grapheneos.camera.ui.gallery.components.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal fun Modifier.dismissGestureInput(
    dismissGestureState: DismissGestureState,
    isZoomed: Boolean,
): Modifier {
    return pointerInput(dismissGestureState, isZoomed) {
        if (!isZoomed) {
            detectDismissGestures(dismissGestureState)
        }
    }
}

private suspend fun PointerInputScope.detectDismissGestures(
    dismissGestureState: DismissGestureState,
) {
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val firstPan = awaitDownwardDrag(touchSlop)

        if (firstPan != null) {
            dragToDismiss(
                dismissGestureState = dismissGestureState,
                origin = down.position,
                firstPan = firstPan,
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitDownwardDrag(
    touchSlop: Float,
): Offset? {
    var pan = Offset.Zero
    var isSingleFinger: Boolean

    do {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        isSingleFinger = event.changes.count { it.pressed } == 1
        pan += event.calculatePan()
    } while (isSingleFinger && pan.getDistance() <= touchSlop)

    return when {
        isSingleFinger && pan.y >= abs(pan.x) -> pan.copy(y = pan.y - touchSlop)
        else -> null
    }
}

private suspend fun AwaitPointerEventScope.dragToDismiss(
    dismissGestureState: DismissGestureState,
    origin: Offset,
    firstPan: Offset,
) {
    dismissGestureState.start(origin)
    dismissGestureState.applyTransform(panChange = firstPan)

    try {
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            dismissGestureState.applyTransform(
                panChange = event.calculatePan(),
                zoomChange = event.calculateZoom(),
                rotationChange = event.calculateRotation(),
            )
            event.consumePositionChanges()
        } while (event.changes.any { it.pressed })

        dismissGestureState.release()
    } finally {
        if (dismissGestureState.isDragging) {
            dismissGestureState.cancel()
        }
    }
}
