package app.grapheneos.camera.ui.components.capturebutton.gesture

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonHoldEnd
import app.grapheneos.camera.ui.components.capturebutton.model.CaptureButtonTarget

internal fun Modifier.captureButtonInput(
    enabled: Boolean,
    holdTargets: List<CaptureButtonTarget>,
    listener: CaptureButtonGestureListener,
    keyHandler: CaptureButtonKeyHandler,
    interactionSource: MutableInteractionSource,
): Modifier {
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick(
                label = null,
                action = {
                    listener.onClick()
                    true
                },
            )
            if (!enabled) {
                disabled()
            }
            if (enabled && listener.isHoldEnabled) {
                customActions = holdAccessibilityActions(
                    targets = holdTargets,
                    listener = listener,
                )
            }
        }
        .onKeyEvent { event ->
            keyHandler.onKeyEvent(
                event = event,
                listener = listener,
            )
        }
        .onFocusChanged { focusState ->
            if (!focusState.isFocused) {
                keyHandler.cancel()
            }
        }
        .focusable(
            enabled = enabled,
            interactionSource = interactionSource,
        )
        .pointerInput(interactionSource) {
            detectCaptureButtonGestures(
                interactionSource = interactionSource,
                listener = listener,
            )
        }
}

private fun holdAccessibilityActions(
    targets: List<CaptureButtonTarget>,
    listener: CaptureButtonGestureListener,
): List<CustomAccessibilityAction> {
    return targets.map { target ->
        CustomAccessibilityAction(
            label = target.accessibilityLabel,
            action = {
                listener.onHoldStart()
                listener.onHoldEnd(CaptureButtonHoldEnd.Committed(target = target))
                true
            },
        )
    }
}
