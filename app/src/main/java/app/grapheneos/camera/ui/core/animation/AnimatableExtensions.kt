package app.grapheneos.camera.ui.core.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

internal suspend fun <T, V : AnimationVector> Animatable<T, V>.follow(
    target: () -> T,
    isAnimated: () -> Boolean,
    animationSpec: AnimationSpec<T>,
) {
    snapshotFlow(target).collectLatest { value ->
        when {
            isAnimated() -> animateTo(
                targetValue = value,
                animationSpec = animationSpec,
            )
            else -> snapTo(value)
        }
    }
}
