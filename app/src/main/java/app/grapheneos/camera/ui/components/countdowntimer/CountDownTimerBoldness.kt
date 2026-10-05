package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.grapheneos.camera.ui.components.motion.PUNCH_SPEC
import kotlin.math.min

internal const val MAX_BOLDNESS = 1.2f

/** One timeline for both phases, so the animator duration scale stretches them together. */
@Stable
internal class CountDownTimerBoldness {

    private val elapsedMillis = Animatable(initialValue = SECOND_MILLIS)
    private var startBoldness by mutableFloatStateOf(0f)

    val value: Float
        get() {
            return boldnessAt(
                elapsedMillis = elapsedMillis.value,
                startBoldness = startBoldness,
            )
        }

    suspend fun punch() {
        startBoldness = value
        elapsedMillis.snapTo(targetValue = 0f)
        elapsedMillis.animateTo(
            targetValue = SECOND_MILLIS,
            animationSpec = tween(
                durationMillis = SECOND_MILLIS.toInt(),
                easing = LinearEasing,
            ),
        )
    }

    companion object {
        private const val SECOND_MILLIS = 1_000f
        private const val PUNCH_MILLIS = 300f
        private const val NANOS_PER_MILLI = 1_000_000L

        internal fun boldnessAt(
            elapsedMillis: Float,
            startBoldness: Float,
        ): Float {
            val thinning = (elapsedMillis - PUNCH_MILLIS) / (SECOND_MILLIS - PUNCH_MILLIS)

            return punchAt(
                elapsedMillis = min(elapsedMillis, PUNCH_MILLIS),
                startBoldness = startBoldness,
            ) * (1f - FastOutSlowInEasing.transform(thinning.coerceIn(0f, 1f)))
        }

        private fun punchAt(
            elapsedMillis: Float,
            startBoldness: Float,
        ): Float {
            return PUNCH_SPEC.getValueFromNanos(
                playTimeNanos = (elapsedMillis * NANOS_PER_MILLI).toLong(),
                initialValue = startBoldness,
                targetValue = 1f,
                initialVelocity = 0f,
            )
        }
    }
}

@Composable
internal fun rememberCountDownTimerBoldness(value: Int): CountDownTimerBoldness {
    val boldness = remember { CountDownTimerBoldness() }

    LaunchedEffect(boldness, value) {
        boldness.punch()
    }

    return boldness
}
