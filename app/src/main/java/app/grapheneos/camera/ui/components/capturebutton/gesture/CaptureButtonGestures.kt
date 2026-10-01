package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
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
    val targetTracker = CaptureButtonTargetTracker(
        targets = listener.holdTargets,
        density = this,
        layoutDirection = listener.layoutDirection,
    )
    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        awaitPressOutcome(
            down = down,
            targetTracker = targetTracker,
        )
    } ?: PressOutcome.Held

    return when (outcome) {
        PressOutcome.Tapped -> {
            listener.onClick()
            true
        }

        PressOutcome.Cancelled -> false

        PressOutcome.Held -> when {
            listener.isEnabled -> awaitHold(
                down = down,
                targetTracker = targetTracker,
                listener = listener,
            )

            else -> awaitRelease()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitPressOutcome(
    down: PointerInputChange,
    targetTracker: CaptureButtonTargetTracker,
): PressOutcome {
    var outcome: PressOutcome? = null

    while (outcome == null) {
        val change = awaitPointerEvent().changes.fastFirstOrNull { it.id == down.id }

        if (change != null) {
            outcome = pressOutcome(
                change = change,
                down = down,
                targetTracker = targetTracker,
            )
        }
        if (outcome == null && isConsumedByOthers(pointerId = down.id)) {
            outcome = PressOutcome.Cancelled
        }
    }

    return outcome
}

private suspend fun AwaitPointerEventScope.isConsumedByOthers(pointerId: PointerId): Boolean {
    val finalChanges = awaitPointerEvent(PointerEventPass.Final).changes

    return finalChanges.fastAny { it.id == pointerId && it.isConsumed }
}

private fun AwaitPointerEventScope.pressOutcome(
    change: PointerInputChange,
    down: PointerInputChange,
    targetTracker: CaptureButtonTargetTracker,
): PressOutcome? {
    return when {
        change.changedToUp() -> {
            change.consume()
            PressOutcome.Tapped
        }

        change.isConsumed -> {
            PressOutcome.Cancelled
        }

        targetTracker.leadsToTarget(
            offset = change.position - down.position,
            touchSlop = viewConfiguration.touchSlop,
        ) -> {
            PressOutcome.Held
        }

        change.isOutOfBounds(size, extendedTouchPadding) -> {
            PressOutcome.Cancelled
        }

        else -> null
    }
}

private suspend fun AwaitPointerEventScope.awaitHold(
    down: PointerInputChange,
    targetTracker: CaptureButtonTargetTracker,
    listener: CaptureButtonGestureListener,
): Boolean {
    var end: CaptureButtonHoldEnd = CaptureButtonHoldEnd.Cancelled

    listener.onHoldStart()

    try {
        end = trackHold(
            down = down,
            targetTracker = targetTracker,
            listener = listener,
        )
    } finally {
        listener.onHoldEnd(end)
    }

    return end != CaptureButtonHoldEnd.Cancelled
}

private suspend fun AwaitPointerEventScope.trackHold(
    down: PointerInputChange,
    targetTracker: CaptureButtonTargetTracker,
    listener: CaptureButtonGestureListener,
): CaptureButtonHoldEnd {
    val dragFilter = CaptureButtonDragFilter(touchSlop = viewConfiguration.touchSlop)
    var end: CaptureButtonHoldEnd? = null

    while (end == null) {
        val change = awaitPointerEvent().changes.fastFirstOrNull { it.id == down.id }

        if (change != null) {
            // Compose delivers a system touch cancel as a release that is already consumed
            val isCancelled = !change.pressed && change.isConsumed

            if (!isCancelled) {
                val offset = change.position - down.position

                listener.onHoldMove(
                    delta = dragFilter.filter(change.positionChange()),
                    offset = offset,
                    armedTarget = targetTracker.update(offset),
                )
            }
            change.consume()
            end = when {
                isCancelled -> CaptureButtonHoldEnd.Cancelled
                change.pressed -> null
                else -> releasedHoldEnd(targetTracker = targetTracker)
            }
        }
    }

    return end
}

private fun releasedHoldEnd(targetTracker: CaptureButtonTargetTracker): CaptureButtonHoldEnd {
    return when (val armedTarget = targetTracker.armedTarget) {
        null -> CaptureButtonHoldEnd.Released
        else -> CaptureButtonHoldEnd.Committed(target = armedTarget)
    }
}

private enum class PressOutcome {
    Tapped,
    Cancelled,
    Held,
}
