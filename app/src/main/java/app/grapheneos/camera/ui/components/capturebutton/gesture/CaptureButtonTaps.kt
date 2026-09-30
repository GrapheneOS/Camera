package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger

internal suspend fun PointerInputScope.detectCaptureButtonTaps(
    interactionSource: MutableInteractionSource,
    isEnabled: () -> Boolean,
    trigger: () -> CaptureButtonTrigger,
    onClick: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()

        if (isEnabled()) {
            down.consume()
            trackTap(
                down = down,
                interactionSource = interactionSource,
                trigger = trigger(),
                onClick = onClick,
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.trackTap(
    down: PointerInputChange,
    interactionSource: MutableInteractionSource,
    trigger: CaptureButtonTrigger,
    onClick: () -> Unit,
) {
    val press = PressInteraction.Press(pressPosition = down.position)
    interactionSource.tryEmit(press)

    if (trigger == CaptureButtonTrigger.Press) {
        onClick()
    }

    var end: PressInteraction = PressInteraction.Cancel(press = press)
    try {
        val up = waitForUpOrCancellation()

        if (up != null) {
            up.consume()
            end = PressInteraction.Release(press = press)
            if (trigger == CaptureButtonTrigger.Release) {
                onClick()
            }
        }
    } finally {
        interactionSource.tryEmit(end)
    }
}
