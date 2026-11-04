package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun rememberLevelIndicatorAngles(
    mode: LevelIndicatorMode,
    roll: () -> Float,
    pitch: () -> Float,
): State<LevelIndicatorFilter.Angles> {
    val currentRoll by rememberUpdatedState(roll)
    val currentPitch by rememberUpdatedState(pitch)
    val filter = remember(mode) { LevelIndicatorFilter(mode = mode) }
    val filteredAngles = remember(filter) {
        mutableStateOf(
            Snapshot.withoutReadObservation {
                filter.update(
                    roll = roll(),
                    pitch = pitch(),
                    elapsed = Duration.ZERO,
                )
            },
        )
    }

    LaunchedEffect(filter) {
        var previousFrameNanos: Long? = null

        snapshotFlow {
            LevelIndicatorFilter.Angles(
                roll = currentRoll(),
                pitch = currentPitch(),
            )
        }.collectLatest { angles ->
            do {
                withFrameNanos { frameNanos ->
                    val elapsed = previousFrameNanos?.let { previous ->
                        (frameNanos - previous).nanoseconds
                    } ?: Duration.ZERO
                    previousFrameNanos = frameNanos
                    filteredAngles.value = filter.update(
                        roll = angles.roll,
                        pitch = angles.pitch,
                        elapsed = elapsed,
                    )
                }
            } while (!filter.isSettled)
        }
    }

    return filteredAngles
}
