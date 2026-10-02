package app.grapheneos.camera.ui.components.ruler

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
import androidx.compose.ui.unit.LayoutDirection

internal fun Modifier.rulerInput(
    state: RulerState,
    rangeInfo: ProgressBarRangeInfo,
    enabled: Boolean,
    layoutDirection: LayoutDirection,
    interactionSource: MutableInteractionSource,
): Modifier {
    return this
        .rulerControls(
            state = state,
            rangeInfo = rangeInfo,
            enabled = enabled,
            layoutDirection = layoutDirection,
            interactionSource = interactionSource,
        )
        .rulerDrag(
            state = state,
            enabled = enabled,
            layoutDirection = layoutDirection,
            interactionSource = interactionSource,
        )
}

internal fun Modifier.rulerControls(
    state: RulerState,
    rangeInfo: ProgressBarRangeInfo,
    enabled: Boolean,
    layoutDirection: LayoutDirection,
    interactionSource: MutableInteractionSource,
): Modifier {
    val isRtl = layoutDirection == LayoutDirection.Rtl

    return this
        .semantics(mergeDescendants = true) {
            progressBarRangeInfo = rangeInfo
            when {
                enabled -> setProgress(action = state::moveTo)
                else -> disabled()
            }
        }
        .onKeyEvent { event ->
            val ticks = keySteps(
                event = event,
                isRtl = isRtl,
            )

            if (enabled && ticks != 0 && event.type == KeyEventType.KeyDown) {
                state.step(ticks = ticks)
            }
            enabled && ticks != 0
        }
        .focusable(
            enabled = enabled,
            interactionSource = interactionSource,
        )
}

internal fun Modifier.rulerDrag(
    state: RulerState,
    enabled: Boolean,
    layoutDirection: LayoutDirection,
    interactionSource: MutableInteractionSource,
    onDragStarted: () -> Unit = {},
): Modifier {
    return this.draggable(
        state = state,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        interactionSource = interactionSource,
        reverseDirection = layoutDirection == LayoutDirection.Rtl,
        onDragStarted = { onDragStarted() },
        onDragStopped = { state.release() },
    )
}

private fun keySteps(
    event: KeyEvent,
    isRtl: Boolean,
): Int {
    val towardEnd = when (event.key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> 0
    }

    return when {
        isRtl -> -towardEnd
        else -> towardEnd
    }
}
