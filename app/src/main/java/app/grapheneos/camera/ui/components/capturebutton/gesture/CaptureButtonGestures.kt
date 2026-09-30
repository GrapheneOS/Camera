package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger

internal suspend fun PointerInputScope.detectCaptureButtonGestures(
    interactionSource: MutableInteractionSource,
    listener: CaptureButtonGestureListener,
) {
    awaitEachGesture {
        val down = awaitFirstDown()

        if (listener.isEnabled) {
            down.consume()
            trackPress(
                down = down,
                interactionSource = interactionSource,
                listener = listener,
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.trackPress(
    down: PointerInputChange,
    interactionSource: MutableInteractionSource,
    listener: CaptureButtonGestureListener,
) {
    val press = PressInteraction.Press(pressPosition = down.position)
    var isReleased = false

    interactionSource.tryEmit(press)
    try {
        isReleased = when {
            listener.trigger == CaptureButtonTrigger.Press -> {
                listener.onClick()
                awaitRelease()
            }

            listener.isHoldEnabled -> awaitTapOrHold(
                down = down,
                listener = listener,
            )

            else -> awaitTap(
                listener = listener,
            )
        }
    } finally {
        interactionSource.tryEmit(
            when {
                isReleased -> PressInteraction.Release(
                    press = press,
                )

                else -> PressInteraction.Cancel(
                    press = press,
                )
            },
        )
    }
}

private suspend fun AwaitPointerEventScope.awaitRelease(): Boolean {
    val up = waitForUpOrCancellation()
    up?.consume()

    return up != null
}

private suspend fun AwaitPointerEventScope.awaitTap(
    listener: CaptureButtonGestureListener,
): Boolean {
    val isReleased = awaitRelease()

    if (isReleased) {
        listener.onClick()
    }

    return isReleased
}

private suspend fun AwaitPointerEventScope.awaitTapOrHold(
    down: PointerInputChange,
    listener: CaptureButtonGestureListener,
): Boolean {
    val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        awaitTap(listener = listener)
    }

    return tap ?: awaitHold(
        down = down,
        listener = listener,
    )
}

private suspend fun AwaitPointerEventScope.awaitHold(
    down: PointerInputChange,
    listener: CaptureButtonGestureListener,
): Boolean {
    var end: CaptureButtonHoldEnd = CaptureButtonHoldEnd.Cancelled

    listener.onHoldStart()

    try {
        end = trackHold(
            down = down,
            listener = listener,
        )
    } finally {
        listener.onHoldEnd(end)
    }

    return end != CaptureButtonHoldEnd.Cancelled
}

private suspend fun AwaitPointerEventScope.trackHold(
    down: PointerInputChange,
    listener: CaptureButtonGestureListener,
): CaptureButtonHoldEnd {
    val dragFilter = CaptureButtonDragFilter(touchSlop = viewConfiguration.touchSlop)
    val targetTracker = CaptureButtonTargetTracker(
        targets = listener.holdTargets,
        density = this,
        layoutDirection = listener.layoutDirection,
    )
    var isHeld = true

    while (isHeld) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }

        if (change != null) {
            val delta = dragFilter.filter(change.positionChange())
            val offset = change.position - down.position

            change.consume()
            isHeld = change.pressed

            listener.onHoldMove(
                delta = delta,
                offset = offset,
                armedTarget = targetTracker.update(offset),
            )
        }
    }

    return when (val armedTarget = targetTracker.armedTarget) {
        null -> CaptureButtonHoldEnd.Released
        else -> CaptureButtonHoldEnd.Committed(target = armedTarget)
    }
}
