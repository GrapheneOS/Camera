package app.grapheneos.camera.ui.components.adjustmentbar.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.adjustmentBarPress(
    enabled: Boolean,
    onPressChanged: (Boolean) -> Unit,
): Modifier {
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput

        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            try {
                onPressChanged(true)
                while (currentEvent.changes.any { change -> change.pressed }) {
                    awaitPointerEvent(pass = PointerEventPass.Final)
                }
            } finally {
                onPressChanged(false)
            }
        }
    }
}
