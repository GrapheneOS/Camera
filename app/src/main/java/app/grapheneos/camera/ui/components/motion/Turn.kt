package app.grapheneos.camera.ui.components.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.collectLatest

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f

@Composable
internal fun animateTurn(
    degrees: () -> Float,
    animationSpec: AnimationSpec<Float>,
): State<Float> {
    val currentDegrees by rememberUpdatedState(degrees)
    val turn = remember {
        Animatable(initialValue = Snapshot.withoutReadObservation { degrees() })
    }

    LaunchedEffect(turn) {
        snapshotFlow { currentDegrees() }.collectLatest { target ->
            turn.animateTo(
                targetValue = turn.value + shortestTurn(
                    from = turn.value,
                    to = target,
                ),
                animationSpec = animationSpec,
            )
        }
    }

    return turn.asState()
}

internal fun shortestTurn(
    from: Float,
    to: Float,
): Float {
    return ((to - from) % FULL_TURN + FULL_TURN + HALF_TURN) % FULL_TURN - HALF_TURN
}
