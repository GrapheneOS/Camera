package app.grapheneos.camera.ui.components.ruler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback

@Stable
internal class RulerBindings(
    val tickSpacing: State<Float>,
    val hapticFeedback: State<HapticFeedback>,
    val value: State<Float>,
    val onValueChange: State<(Float) -> Unit>,
    val onValueChangeFinished: State<() -> Unit>,
)

@Composable
internal fun rememberRulerBindings(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    tickSpacing: Float,
): RulerBindings {
    val currentTickSpacing = rememberUpdatedState(tickSpacing)
    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)

    return remember {
        RulerBindings(
            tickSpacing = currentTickSpacing,
            hapticFeedback = currentHapticFeedback,
            value = currentValue,
            onValueChange = currentOnValueChange,
            onValueChangeFinished = currentOnValueChangeFinished,
        )
    }
}
