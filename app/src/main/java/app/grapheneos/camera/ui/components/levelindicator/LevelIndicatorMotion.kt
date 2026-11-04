package app.grapheneos.camera.ui.components.levelindicator

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import app.grapheneos.camera.ui.components.levelindicator.model.LevelIndicatorMode
import app.grapheneos.camera.ui.components.motion.SETTLE_SPEC
import kotlinx.coroutines.flow.collectLatest

@Stable
internal class LevelIndicatorMotion(
    val mode: LevelIndicatorMode,
    roll: State<Float>,
    pitch: State<Float>,
    rollAlignmentFraction: State<Float>,
    pitchAlignmentFraction: State<Float>,
    verticalAlignmentFraction: State<Float>,
) {
    val roll by roll
    val pitch by pitch
    val rollAlignmentFraction by rollAlignmentFraction
    val pitchAlignmentFraction by pitchAlignmentFraction
    val verticalAlignmentFraction by verticalAlignmentFraction
}

@Composable
internal fun rememberLevelIndicatorMotion(
    mode: LevelIndicatorMode,
    roll: () -> Float,
    pitch: () -> Float,
): LevelIndicatorMotion {
    val shownAngles = rememberLevelIndicatorAngles(
        mode = mode,
        roll = roll,
        pitch = pitch,
    )
    val shownRoll = remember(shownAngles) { derivedStateOf { shownAngles.value.roll } }
    val shownPitch = remember(shownAngles) { derivedStateOf { shownAngles.value.pitch } }
    val rollAlignmentFraction = animateAlignmentFraction {
        LevelZone.isRollLevel(roll = shownRoll.value)
    }
    val pitchAlignmentFraction = animateAlignmentFraction {
        LevelZone.isPitchLevel(pitch = shownPitch.value)
    }
    val verticalAlignmentFraction = animateAlignmentFraction {
        LevelZone.isVertical(pitch = shownPitch.value)
    }

    LevelIndicatorHapticEffect(
        mode = mode,
        roll = shownRoll,
        pitch = shownPitch,
    )

    return remember(
        mode,
        shownRoll,
        shownPitch,
        rollAlignmentFraction,
        pitchAlignmentFraction,
        verticalAlignmentFraction,
    ) {
        LevelIndicatorMotion(
            mode = mode,
            roll = shownRoll,
            pitch = shownPitch,
            rollAlignmentFraction = rollAlignmentFraction,
            pitchAlignmentFraction = pitchAlignmentFraction,
            verticalAlignmentFraction = verticalAlignmentFraction,
        )
    }
}

@Composable
private fun animateAlignmentFraction(isAligned: () -> Boolean): State<Float> {
    val currentIsAligned by rememberUpdatedState(isAligned)
    val fraction = remember {
        val initiallyAligned = Snapshot.withoutReadObservation(isAligned)
        Animatable(initialValue = alignmentFraction(isAligned = initiallyAligned))
    }

    LaunchedEffect(fraction) {
        snapshotFlow { currentIsAligned() }.collectLatest { aligned ->
            fraction.animateTo(
                targetValue = alignmentFraction(isAligned = aligned),
                animationSpec = SETTLE_SPEC,
            )
        }
    }

    return fraction.asState()
}

private fun alignmentFraction(isAligned: Boolean): Float {
    return when {
        isAligned -> 1f
        else -> 0f
    }
}
