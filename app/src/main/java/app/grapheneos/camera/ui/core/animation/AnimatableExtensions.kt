package app.grapheneos.camera.ui.core.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

private const val BLINK_PHASE_MILLIS = 250

internal suspend fun Animatable<Float, *>.blink(times: Int) {
    val phase = tween<Float>(
        durationMillis = BLINK_PHASE_MILLIS,
        easing = LinearEasing,
    )

    repeat(times) {
        animateTo(targetValue = 1f, animationSpec = phase)
        animateTo(targetValue = 0f, animationSpec = phase)
    }
}

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
