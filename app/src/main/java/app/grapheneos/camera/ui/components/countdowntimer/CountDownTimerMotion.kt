package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.withTimeoutOrNull

internal const val MAX_BOLDNESS = 1.2f

private const val PUNCH_MILLIS = 500L

internal val PUNCH_SPEC = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 500f,
)
private val THIN_SPEC = tween<Float>(
    durationMillis = 500,
    easing = FastOutSlowInEasing,
)

/** Thins out over the second half of each second, in step with the countdown ring. */
@Composable
internal fun animateCountDownTimerBoldness(value: Int): State<Float> {
    val boldness = remember { Animatable(initialValue = 0f) }

    LaunchedEffect(value) {
        val isAnimated = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f

        if (isAnimated) {
            withTimeoutOrNull(timeMillis = PUNCH_MILLIS) {
                boldness.animateTo(
                    targetValue = 1f,
                    animationSpec = PUNCH_SPEC,
                )
            }
            boldness.animateTo(
                targetValue = 0f,
                animationSpec = THIN_SPEC,
            )
        }
    }

    return boldness.asState()
}
