package app.grapheneos.camera.ui.components.zoom.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.LayoutDirection
import app.grapheneos.camera.ui.components.ruler.rulerDrag

@Stable
internal class ZoomControlExpander(
    private val enabled: State<Boolean>,
    private val expanded: State<Boolean>,
    private val onExpand: State<() -> Unit>,
    private val onPressChanged: State<(Boolean) -> Unit>,
    private val hapticFeedback: State<HapticFeedback>,
) {

    fun isCollapsed(): Boolean {
        return !expanded.value
    }

    fun expand() {
        onExpand.value()
    }

    fun reportPress(pressed: Boolean) {
        onPressChanged.value(pressed)
    }

    fun expandOnDrag() {
        if (isCollapsed()) {
            expand()
        }
    }

    fun expandOnLongPress() {
        if (enabled.value && isCollapsed()) {
            hapticFeedback.value.performHapticFeedback(HapticFeedbackType.LongPress)
            expand()
        }
    }
}

@Composable
internal fun rememberZoomControlExpander(
    enabled: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
): ZoomControlExpander {
    val currentEnabled = rememberUpdatedState(enabled)
    val currentExpanded = rememberUpdatedState(expanded)
    val currentOnExpand = rememberUpdatedState(onExpand)
    val currentOnPressChanged = rememberUpdatedState(onPressChanged)
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)

    return remember {
        ZoomControlExpander(
            enabled = currentEnabled,
            expanded = currentExpanded,
            onExpand = currentOnExpand,
            onPressChanged = currentOnPressChanged,
            hapticFeedback = currentHapticFeedback,
        )
    }
}

internal fun Modifier.zoomControlInput(
    state: ZoomBarState,
    expander: ZoomControlExpander,
    enabled: Boolean,
    layoutDirection: LayoutDirection,
    interactionSource: MutableInteractionSource,
): Modifier {
    return this
        .rulerDrag(
            state = state,
            enabled = enabled,
            layoutDirection = layoutDirection,
            interactionSource = interactionSource,
            onDragStarted = expander::expandOnDrag,
        )
        .zoomControlPress(
            expander = expander,
            enabled = enabled,
            interactionSource = interactionSource,
        )
}

private fun Modifier.zoomControlPress(
    expander: ZoomControlExpander,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
): Modifier {
    return pointerInput(expander, interactionSource, enabled) {
        if (!enabled) return@pointerInput

        awaitEachGesture {
            val press = PressInteraction.Press(awaitFirstDown(requireUnconsumed = false).position)
            var end: PressInteraction = PressInteraction.Cancel(press)

            interactionSource.tryEmit(press)
            try {
                expander.reportPress(pressed = true)
                val isLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation()
                    false
                } ?: true

                if (isLongPress) {
                    expander.expandOnLongPress()
                }
                awaitRelease()
                end = PressInteraction.Release(press)
            } finally {
                interactionSource.tryEmit(end)
                expander.reportPress(pressed = false)
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitRelease() {
    while (currentEvent.changes.any { change -> change.pressed }) {
        awaitPointerEvent(pass = PointerEventPass.Final)
    }
}
