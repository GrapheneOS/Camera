package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode

@Composable
internal fun LevelIndicatorHapticEffect(
    mode: LevelIndicatorMode,
    roll: State<Float>,
    pitch: State<Float>,
) {
    fun currentZone(): LevelZone {
        return LevelZone.of(
            mode = mode,
            roll = roll.value,
            pitch = pitch.value,
        )
    }

    val currentHapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)

    LaunchedEffect(mode, roll, pitch) {
        val tracker = LevelHapticTracker(initialZone = currentZone())

        snapshotFlow { currentZone() }.collect { zone ->
            if (tracker.reachesLevel(zone = zone)) {
                currentHapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }
}
