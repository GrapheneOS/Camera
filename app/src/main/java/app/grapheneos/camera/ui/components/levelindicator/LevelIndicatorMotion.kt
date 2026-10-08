package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import app.grapheneos.camera.ui.components.motion.animateTurn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Stable
internal class LevelIndicatorMotion(
    roll: State<Float>,
    pitch: State<Float>,
    rollLevel: State<Float>,
    pitchLevel: State<Float>,
    verticalLevel: State<Float>,
    topDown: State<Float>,
) {
    val roll by roll
    val pitch by pitch
    val rollLevel by rollLevel
    val pitchLevel by pitchLevel
    val verticalLevel by verticalLevel
    val topDown by topDown
}

@Composable
internal fun rememberLevelIndicatorMotion(
    roll: () -> Float,
    pitch: () -> Float,
): LevelIndicatorMotion {
    val shownRoll = animateTurn(
        degrees = roll,
        animationSpec = SETTLE_SPEC,
    )
    val shownPitch = animateTurn(
        degrees = pitch,
        animationSpec = SETTLE_SPEC,
    )
    val rollLevel = animateLevelFraction { LevelZone.isRollLevel(roll = shownRoll.value) }
    val pitchLevel = animateLevelFraction { LevelZone.isPitchLevel(pitch = shownPitch.value) }
    val verticalLevel = animateLevelFraction { LevelZone.isVertical(pitch = shownPitch.value) }
    val topDown = animateTopDownFraction(pitch = shownPitch)

    LevelHapticEffect(
        roll = shownRoll,
        pitch = shownPitch,
    )

    return remember(shownRoll, shownPitch, rollLevel, pitchLevel, verticalLevel, topDown) {
        LevelIndicatorMotion(
            roll = shownRoll,
            pitch = shownPitch,
            rollLevel = rollLevel,
            pitchLevel = pitchLevel,
            verticalLevel = verticalLevel,
            topDown = topDown,
        )
    }
}

@Composable
private fun animateLevelFraction(isLevel: () -> Boolean): State<Float> {
    val currentIsLevel by rememberUpdatedState(isLevel)
    val fraction = remember {
        Animatable(initialValue = fractionOf(level = Snapshot.withoutReadObservation(isLevel)))
    }

    LaunchedEffect(fraction) {
        snapshotFlow { currentIsLevel() }.collectLatest { level ->
            fraction.animateTo(
                targetValue = fractionOf(level = level),
                animationSpec = SETTLE_SPEC,
            )
        }
    }

    return fraction.asState()
}

@Composable
private fun animateTopDownFraction(pitch: State<Float>): State<Float> {
    val fraction = remember {
        val isTopDown = Snapshot.withoutReadObservation {
            LevelZone.isTopDown(
                pitch = pitch.value,
                wasTopDown = false,
            )
        }

        Animatable(initialValue = fractionOf(level = isTopDown))
    }

    LaunchedEffect(pitch) {
        var isTopDown = fraction.value == 1f

        snapshotFlow { pitch.value }
            .map { value ->
                isTopDown = LevelZone.isTopDown(
                    pitch = value,
                    wasTopDown = isTopDown,
                )
                isTopDown
            }
            .distinctUntilChanged()
            .collectLatest { topDown ->
                fraction.animateTo(
                    targetValue = fractionOf(level = topDown),
                    animationSpec = SETTLE_SPEC,
                )
            }
    }

    return fraction.asState()
}

@Composable
private fun LevelHapticEffect(
    roll: State<Float>,
    pitch: State<Float>,
) {
    fun currentZone(): LevelZone {
        return LevelZone.of(
            roll = roll.value,
            pitch = pitch.value,
        )
    }

    val hapticFeedback = rememberUpdatedState(LocalHapticFeedback.current)

    LaunchedEffect(roll, pitch) {
        val tracker = LevelTracker(initialZone = currentZone())

        snapshotFlow { currentZone() }.collect { zone ->
            if (tracker.reachesLevel(zone = zone)) {
                hapticFeedback.value.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }
}

private fun fractionOf(level: Boolean): Float {
    return when {
        level -> 1f
        else -> 0f
    }
}
