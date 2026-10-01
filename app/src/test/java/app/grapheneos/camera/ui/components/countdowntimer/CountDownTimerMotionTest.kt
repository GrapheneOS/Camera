package app.grapheneos.camera.ui.components.countdowntimer

import androidx.compose.animation.core.FloatSpringSpec
import org.junit.Assert.assertTrue
import org.junit.Test

class CountDownTimerMotionTest {

    @Test
    fun punch_overshootStaysWithinMaxBoldness() {
        val spring = FloatSpringSpec(
            dampingRatio = PUNCH_SPEC.dampingRatio,
            stiffness = PUNCH_SPEC.stiffness,
        )
        val peak = (0..SETTLE_MILLIS).maxOf { millis ->
            spring.getValueFromNanos(
                playTimeNanos = millis * NANOS_PER_MILLI,
                initialValue = 0f,
                targetValue = 1f,
                initialVelocity = 0f,
            )
        }

        assertTrue("peak $peak", peak in 1f..MAX_BOLDNESS)
    }

    private companion object {
        private const val SETTLE_MILLIS = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
