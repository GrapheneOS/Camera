package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
        trigger: CaptureButtonTrigger,
        onClick: () -> Unit,
    ): Boolean {
        val isClickKey = event.key in CLICK_KEYS

        if (isClickKey) {
            when (event.type) {
                KeyEventType.KeyDown -> onKeyDown(
                    trigger = trigger,
                    onClick = onClick,
                )

                KeyEventType.KeyUp -> onKeyUp(
                    onClick = onClick,
                )
            }
        }

        return isClickKey
    }

    private fun onKeyDown(
        trigger: CaptureButtonTrigger,
        onClick: () -> Unit,
    ) {
        if (press != null) return

        val newPress = PressInteraction.Press(pressPosition = Offset.Zero)
        press = newPress
        pressTrigger = trigger
        interactionSource.tryEmit(newPress)

        if (trigger == CaptureButtonTrigger.Press) {
            onClick()
        }
    }

    private fun onKeyUp(
        onClick: () -> Unit,
    ) {
        val releasedPress = press ?: return

        press = null
        interactionSource.tryEmit(PressInteraction.Release(press = releasedPress))
        if (pressTrigger == CaptureButtonTrigger.Release) {
            onClick()
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
