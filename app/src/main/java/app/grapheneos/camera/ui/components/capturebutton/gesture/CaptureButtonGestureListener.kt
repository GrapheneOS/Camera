package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTrigger

internal interface CaptureButtonGestureListener {

    val isEnabled: Boolean
    val trigger: CaptureButtonTrigger
    val isHoldEnabled: Boolean

    fun onClick()

    fun onHoldStart()

    fun onHoldDrag(delta: Offset)

    fun onHoldEnd(end: CaptureButtonHoldEnd)
}

@Composable
internal fun rememberCaptureButtonGestureListener(
    enabled: Boolean,
    trigger: CaptureButtonTrigger,
    onClick: () -> Unit,
    onHoldStart: (() -> Unit)?,
    onHoldDrag: (Offset) -> Unit,
    onHoldEnd: (CaptureButtonHoldEnd) -> Unit,
): CaptureButtonGestureListener {
    val hapticFeedback by rememberUpdatedState(LocalHapticFeedback.current)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTrigger by rememberUpdatedState(trigger)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldDrag by rememberUpdatedState(onHoldDrag)
    val currentOnHoldEnd by rememberUpdatedState(onHoldEnd)

    return remember {
        object : CaptureButtonGestureListener {

            override val isEnabled: Boolean
                get() {
                    return currentEnabled
                }

            override val trigger: CaptureButtonTrigger
                get() {
                    return currentTrigger
                }

            override val isHoldEnabled: Boolean
                get() {
                    return currentOnHoldStart != null
                }

            override fun onClick() {
                currentOnClick()
            }

            override fun onHoldStart() {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnHoldStart?.invoke()
            }

            override fun onHoldDrag(delta: Offset) {
                currentOnHoldDrag(delta)
            }

            override fun onHoldEnd(end: CaptureButtonHoldEnd) {
                currentOnHoldEnd(end)
            }
        }
    }
}
