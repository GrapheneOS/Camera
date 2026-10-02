package app.grapheneos.camera.ui.components.countdowntimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountDownTimerMotionTest {

    @Test
    fun boldness_startsWhereThePreviousSecondLeftIt() {
        assertEquals(0.3f, boldnessAt(elapsedMillis = 0f, startBoldness = 0.3f))
    }

    @Test
    fun boldness_overshootStaysWithinMaxBoldness() {
        val peak = (0..SECOND_MILLIS).maxOf { millis ->
            boldnessAt(elapsedMillis = millis.toFloat(), startBoldness = 0f)
        }

        assertTrue("peak $peak", peak in 1f..MAX_BOLDNESS)
    }

    @Test
    fun boldness_isAtRestWhenTheSecondEnds() {
        val boldness = boldnessAt(
            elapsedMillis = SECOND_MILLIS.toFloat(),
            startBoldness = 0f,
        )

        assertEquals(0f, boldness)
    }

    private fun boldnessAt(
        elapsedMillis: Float,
        startBoldness: Float,
    ): Float {
        return CountDownTimerBoldness.boldnessAt(
            elapsedMillis = elapsedMillis,
            startBoldness = startBoldness,
        )
    }

    private companion object {
        private const val SECOND_MILLIS = 1_000
    }
}
