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

@Composable
internal fun animateFollow(
    value: () -> Float,
    animationSpec: AnimationSpec<Float>,
): State<Float> {
    val currentValue by rememberUpdatedState(value)
    val follower = remember {
        Animatable(initialValue = Snapshot.withoutReadObservation(value))
    }

    LaunchedEffect(follower) {
        snapshotFlow { currentValue() }.collectLatest { target ->
            follower.animateTo(
                targetValue = target,
                animationSpec = animationSpec,
            )
        }
    }

    return follower.asState()
}
