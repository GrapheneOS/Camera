package app.grapheneos.camera.ui.components.adjustmentbar.gesture

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import app.grapheneos.camera.ui.components.adjustmentbar.AdjustmentBarScale

internal fun Modifier.adjustmentBarInput(
    value: Float,
    scale: AdjustmentBarScale,
    state: AdjustmentBarState,
    enabled: Boolean,
    isRtl: Boolean,
    interactionSource: MutableInteractionSource,
): Modifier {
    return this
        .semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = value,
                range = scale.valueRange,
                steps = scale.steps,
            )
            when {
                enabled -> setProgress { target ->
                    state.moveTo(tick = scale.tickOf(value = target))
                }

                else -> disabled()
            }
        }
        .onKeyEvent { event ->
            enabled && onKeyEvent(
                event = event,
                isRtl = isRtl,
                state = state,
            )
        }
        .focusable(
            enabled = enabled,
            interactionSource = interactionSource,
        )
        .draggable(
            state = state,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            interactionSource = interactionSource,
            reverseDirection = isRtl,
            onDragStopped = { state.release() },
        )
}

private fun onKeyEvent(
    event: KeyEvent,
    isRtl: Boolean,
    state: AdjustmentBarState,
): Boolean {
    val towardEnd = when (event.key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> 0
    }
    val ticks = when {
        isRtl -> -towardEnd
        else -> towardEnd
    }

    if (ticks != 0 && event.type == KeyEventType.KeyDown) {
        state.step(ticks = ticks)
    }

    return ticks != 0
}
