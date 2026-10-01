package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger

internal class CaptureButtonKeyHandler(
    private val interactionSource: MutableInteractionSource,
) {

    private var press: PressInteraction.Press? = null
    private var pressTrigger = CaptureButtonTrigger.Release

    fun onKeyEvent(
        event: KeyEvent,
        listener: CaptureButtonGestureListener,
    ): Boolean {
        val isClickKey = event.key in CLICK_KEYS

        if (isClickKey) {
            when (event.type) {
                KeyEventType.KeyDown -> onKeyDown(listener = listener)
                KeyEventType.KeyUp -> onKeyUp(listener = listener)
            }
        }

        return isClickKey
    }

    private fun onKeyDown(listener: CaptureButtonGestureListener) {
        if (press != null) return

        val newPress = PressInteraction.Press(pressPosition = Offset.Zero)
        press = newPress
        pressTrigger = listener.trigger
        interactionSource.tryEmit(newPress)

        if (pressTrigger == CaptureButtonTrigger.Press) {
            listener.onClick()
        }
    }

    private fun onKeyUp(listener: CaptureButtonGestureListener) {
        val releasedPress = press ?: return

        press = null
        interactionSource.tryEmit(PressInteraction.Release(press = releasedPress))
        if (pressTrigger == CaptureButtonTrigger.Release) {
            listener.onClick()
        }
    }

    private companion object {
        private val CLICK_KEYS = setOf(
            Key.Enter,
            Key.NumPadEnter,
            Key.DirectionCenter,
            Key.Spacebar,
        )
    }
}

@Composable
internal fun rememberCaptureButtonKeyHandler(
    interactionSource: MutableInteractionSource,
): CaptureButtonKeyHandler {
    return remember(interactionSource) {
        CaptureButtonKeyHandler(interactionSource = interactionSource)
    }
}
