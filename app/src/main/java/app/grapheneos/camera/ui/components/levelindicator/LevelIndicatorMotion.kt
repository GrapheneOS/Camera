package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun animateAngle(angle: () -> Float): State<Float> {
    val currentAngle by rememberUpdatedState(angle)
    val animatable = remember {
        Animatable(initialValue = Snapshot.withoutReadObservation { angle() })
    }

    LaunchedEffect(animatable) {
        snapshotFlow { currentAngle() }.collectLatest { target ->
            animatable.animateTo(
                targetValue = target,
                animationSpec = SETTLE_SPEC,
            )
        }
    }

    return animatable.asState()
}

@Composable
internal fun animateLevelFraction(isLevel: () -> Boolean): State<Float> {
    fun fractionOf(level: Boolean): Float {
        return when {
            level -> 1f
            else -> 0f
        }
    }

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
internal fun LevelHapticEffect(
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
